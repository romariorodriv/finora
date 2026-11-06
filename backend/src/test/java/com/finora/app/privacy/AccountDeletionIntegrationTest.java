package com.finora.app.privacy;

import com.finora.app.auth.*;
import com.finora.app.auth.google.*;
import com.finora.app.gmail.client.GoogleGmailClient;
import com.finora.app.gmail.entity.GmailConnection;
import com.finora.app.gmail.repository.GmailConnectionRepository;
import com.finora.app.transaction.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:account-deletion-qa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccountSessions sessions;
    @Autowired TransactionRepository transactions;
    @Autowired GmailConnectionRepository connections;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GoogleGmailClient gmail;

    record Fixture(Long id, Cookie cookie, String email, String subject) {}

    Fixture user() {
        var nonce = UUID.randomUUID().toString();
        String email="delete-"+nonce+"@example.test";
        String subject="google-"+nonce;
        var u = new User("Deletion QA",email,"legacy-hash");
        u.googleSubject=subject;u.role="USER";
        users.saveAndFlush(u);
        return new Fixture(u.id,new Cookie(AuthCookies.SESSION,sessions.issue(u.id)),email,subject);
    }

    @Test void confirmationAndFirstPartySessionAreMandatory() throws Exception {
        var a=user();
        mvc.perform(post("/api/v1/account/deletion-request").cookie(a.cookie())
                .header("Origin","http://localhost:4200").contentType("application/json")
                .content("{\"confirmed\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));
        mvc.perform(post("/api/v1/account/deletion-request").cookie(a.cookie())
                .header("Origin","https://evil.example.test").contentType("application/json")
                .content("{\"confirmed\":true,\"confirmationText\":\"ELIMINAR\"}"))
            .andExpect(status().isForbidden());
        assertTrue(users.existsById(a.id()));
    }

    @Test void deletesFinancialAndGmailDataAndAllowsFreshAccount() throws Exception {
        var a=user();
        var other=user();
        var tx=new Transaction();
        tx.userId=a.id();tx.description="Compra QA";tx.category="Otros";
        tx.amount=new BigDecimal("20.00");tx.date=LocalDate.now();
        transactions.saveAndFlush(tx);
        var otherTx=new Transaction();
        otherTx.userId=other.id();otherTx.description="Otro usuario";otherTx.category="Otros";
        otherTx.amount=new BigDecimal("50.00");otherTx.date=LocalDate.now();
        transactions.saveAndFlush(otherTx);

        var mailbox=new GmailConnection();
        mailbox.userId=a.id();mailbox.gmailAddress=a.email();
        connections.saveAndFlush(mailbox);

        var response=mvc.perform(post("/api/v1/account/deletion-request")
                .cookie(a.cookie()).header("Origin","http://localhost:4200")
                .contentType("application/json")
                .content("{\"confirmed\":true,\"confirmationText\":\"ELIMINAR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELETED"))
            .andReturn().getResponse();

        assertEquals(0, response.getCookie(AuthCookies.SESSION).getMaxAge());
        assertFalse(users.existsById(a.id()));
        assertTrue(users.findByGoogleSubject(a.subject()).isEmpty());
        assertTrue(transactions.findById(tx.id).isEmpty());
        assertTrue(connections.findById(mailbox.id).isEmpty());
        assertTrue(users.existsById(other.id()));
        assertTrue(transactions.findById(otherTx.id).isPresent());
        mvc.perform(get("/api/v1/account").cookie(a.cookie()))
            .andExpect(status().isUnauthorized());

        // Google can provision this identity again, with a different user id.
        var fresh=new User("Rejoined",a.email(),"new-hash");
        fresh.googleSubject=a.subject();users.saveAndFlush(fresh);
        assertNotEquals(a.id(),fresh.id);
        assertEquals("USER",fresh.role);
        assertEquals(0,jdbc.queryForObject(
            "SELECT count(*) FROM transactions WHERE user_id=?",Integer.class,fresh.id));
    }
}
