package com.finora.app.privacy;

import com.finora.app.auth.UserRepository;
import com.finora.app.gmail.service.GmailOAuthService;
import com.finora.app.shared.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service, irreversible account removal.
 *
 * Deletes only data owned by the authenticated account. A later Google login
 * creates a new app_users row and does not restore this account.
 * External Gmail revocation is best effort; credentials are removed locally.
 *
 * Production must have the reviewed user ownership / FK schema and backups.
 */
@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final GmailOAuthService gmail;
    private final JdbcTemplate sql;

    public AccountDeletionService(UserRepository users, GmailOAuthService gmail, JdbcTemplate sql) {
        this.users = users;
        this.gmail = gmail;
        this.sql = sql;
    }

    @Transactional
    public void delete(Long userId) {
        var user = users.locked(userId)
            .orElseThrow(() -> new ApiException(401, "ACCOUNT_NOT_FOUND", "La sesión ya no es válida."));

        // An administrator may own cross-user classification batches and audits.
        // Never silently remove other users' records while deleting an admin.
        if ("ADMIN".equals(user.role)) {
            throw new ApiException(409, "ADMIN_OWNERSHIP_TRANSFER_REQUIRED",
                "Esta cuenta administra registros compartidos. Transfiere el rol administrador antes de eliminarla.");
        }

        // Cancel active imports and revoke Google permissions when possible.
        gmail.disconnect(userId);

        // Remove historic audit snapshots associated with transactions or personal rules.
        // Shared category/rule catalogs owned by the platform must survive.
        sql.update("""
            DELETE FROM admin_audit
            WHERE actor_id = ?
               OR batch_id IN (
                  SELECT DISTINCT batch_id FROM classification_changes
                  WHERE transaction_id IN (SELECT id FROM transactions WHERE user_id = ?)
               )
               OR (entity_type = 'TRANSACTION' AND entity_id IN (
                  SELECT CAST(id AS VARCHAR) FROM transactions WHERE user_id = ?
               ))
               OR (entity_type = 'RULE' AND entity_id IN (
                  SELECT CAST(id AS VARCHAR) FROM classification_rules WHERE user_id = ?
               ))
            """, userId, userId, userId, userId);
        sql.update("""
            DELETE FROM classification_changes
            WHERE transaction_id IN (SELECT id FROM transactions WHERE user_id = ?)
            """, userId);

        // Detach any shared review batches from private user rules before deletion.
        sql.update("""
            UPDATE classification_batches SET rule_id = NULL
            WHERE rule_id IN (SELECT id FROM classification_rules WHERE user_id = ?)
            """, userId);

        // Gmail processing records and tokens (child records before mailbox rows).
        sql.update("DELETE FROM gmail_job_items WHERE user_id = ?", userId);
        sql.update("DELETE FROM gmail_sync_jobs WHERE user_id = ?", userId);
        sql.update("DELETE FROM imported_messages WHERE user_id = ?", userId);
        sql.update("DELETE FROM gmail_oauth_states WHERE user_id = ?", userId);
        sql.update("DELETE FROM gmail_connections WHERE user_id = ?", userId);

        // Financial data, feedback and session/link records.
        sql.update("DELETE FROM planning_payments WHERE user_id = ?", userId);
        sql.update("DELETE FROM planning_profiles WHERE user_id = ?", userId);
        sql.update("DELETE FROM transactions WHERE user_id = ?", userId);
        // Transaction references to personal rules have now been removed.
        sql.update("DELETE FROM classification_rules WHERE user_id = ?", userId);
        sql.update("DELETE FROM user_feedback WHERE user_id = ?", userId);
        sql.update("DELETE FROM account_sessions WHERE user_id = ?", userId);
        sql.update("""
            DELETE FROM google_auth_flows WHERE link_user_id = ?
               OR google_subject = ?
            """, userId, user.googleSubject);
        sql.update("DELETE FROM data_deletion_requests WHERE user_id = ?", userId);

        // Delete credentials and stable identity last; foreign keys then guard
        // against future unhandled child rows, rolling back on any failure.
        int deleted = sql.update("DELETE FROM app_users WHERE id = ?", userId);
        if (deleted != 1) throw new IllegalStateException("Account deletion did not delete the user");
    }
}
