# Archivos de la integración Google y Gmail

Rama de trabajo existente: feature/sarela-2.2-premium-ui. No se modificó main ni se hicieron despliegues. El código se publica en esta rama a petición del propietario.

## Nuevos (43)

- `backend/sql/V2_4__google_onboarding_gmail_jobs.sql`
- `backend/sql/check_google_onboarding.sql`
- `backend/sql/verify_google_onboarding.sql`
- `backend/src/main/java/com/finora/app/auth/google/AccountSession.java`
- `backend/src/main/java/com/finora/app/auth/google/AccountSessionRepository.java`
- `backend/src/main/java/com/finora/app/auth/google/AccountSessions.java`
- `backend/src/main/java/com/finora/app/auth/google/AuthCookies.java`
- `backend/src/main/java/com/finora/app/auth/google/AuthMaintenance.java`
- `backend/src/main/java/com/finora/app/auth/google/AuthSecrets.java`
- `backend/src/main/java/com/finora/app/auth/google/GoogleAuthController.java`
- `backend/src/main/java/com/finora/app/auth/google/GoogleAuthFlow.java`
- `backend/src/main/java/com/finora/app/auth/google/GoogleAuthFlowRepository.java`
- `backend/src/main/java/com/finora/app/auth/google/GoogleIdTokenVerifier.java`
- `backend/src/main/java/com/finora/app/auth/google/GoogleIdentityClient.java`
- `backend/src/main/java/com/finora/app/auth/google/GoogleSignInService.java`
- `backend/src/main/java/com/finora/app/auth/google/SessionOriginFilter.java`
- `backend/src/main/java/com/finora/app/config/SchemaPreflight.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailImportRunner.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailJobItem.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailJobItemRepository.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailJobScheduler.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailJobStore.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailMessageProcessor.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailSyncJob.java`
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailSyncJobRepository.java`
- `backend/src/main/java/com/finora/app/privacy/DeletionRequest.java`
- `backend/src/main/java/com/finora/app/privacy/DeletionRequestRepository.java`
- `backend/src/main/java/com/finora/app/privacy/PrivacyController.java`
- `backend/src/test/java/com/finora/app/auth/google/AuthCookiesTest.java`
- `backend/src/test/java/com/finora/app/auth/google/GoogleIdentityVerificationTest.java`
- `backend/src/test/java/com/finora/app/auth/google/GoogleSignInIntegrationTest.java`
- `backend/src/test/java/com/finora/app/gmail/client/GoogleGmailClientTest.java`
- `backend/src/test/java/com/finora/app/gmail/jobs/GmailQueueIntegrationTest.java`
- `backend/src/test/java/com/finora/app/gmail/service/GmailOAuthIntegrationTest.java`
- `backend/src/test/java/com/finora/app/gmail/service/GmailOAuthServiceTest.java`
- `backend/src/test/java/com/finora/app/importer/PeruBankParserOnboardingTest.java`
- `docs/GOOGLE_GMAIL_ONBOARDING.md`
- `docs/google-onboarding.env.example`
- `frontend/public/eliminar-datos.html`
- `frontend/public/privacidad.html`
- `frontend/public/terminos.html`
- `frontend/scripts/publish-google-frontend.ps1`
- `frontend/src/app/core/services/google-auth-http.spec.ts`

## Modificados (50)

- `README.md`
- `backend/pom.xml`
- `backend/sql/grant_admin.sql`
- `backend/src/main/java/com/finora/app/auth/AuthController.java`
- `backend/src/main/java/com/finora/app/auth/AuthDtos.java`
- `backend/src/main/java/com/finora/app/auth/User.java`
- `backend/src/main/java/com/finora/app/auth/UserRepository.java`
- `backend/src/main/java/com/finora/app/config/JwtFilter.java`
- `backend/src/main/java/com/finora/app/config/SecurityConfig.java`
- `backend/src/main/java/com/finora/app/dashboard/DashboardService.java`
- `backend/src/main/java/com/finora/app/gmail/client/GmailClient.java`
- `backend/src/main/java/com/finora/app/gmail/client/GoogleGmailClient.java`
- `backend/src/main/java/com/finora/app/gmail/controller/GmailController.java`
- `backend/src/main/java/com/finora/app/gmail/dto/GmailDtos.java`
- `backend/src/main/java/com/finora/app/gmail/entity/GmailOAuthState.java`
- `backend/src/main/java/com/finora/app/gmail/entity/ImportedMessage.java`
- `backend/src/main/java/com/finora/app/gmail/repository/GmailConnectionRepository.java`
- `backend/src/main/java/com/finora/app/gmail/repository/GmailOAuthStateRepository.java`
- `backend/src/main/java/com/finora/app/gmail/repository/ImportedMessageRepository.java`
- `backend/src/main/java/com/finora/app/gmail/security/TokenCipher.java`
- `backend/src/main/java/com/finora/app/gmail/service/GmailOAuthService.java`
- `backend/src/main/java/com/finora/app/importer/ImportDtos.java`
- `backend/src/main/java/com/finora/app/importer/ImportService.java`
- `backend/src/main/java/com/finora/app/importer/PeruBankParser.java`
- `backend/src/main/java/com/finora/app/shared/error/GlobalExceptionHandler.java`
- `backend/src/main/java/com/finora/app/transaction/Transaction.java`
- `backend/src/main/java/com/finora/app/transaction/TransactionResponse.java`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/finora/app/admin/AdminIntegrationTest.java`
- `backend/src/test/java/com/finora/app/importer/ImportServiceTest.java`
- `backend/src/test/java/com/finora/app/planning/PlanningAuthorizationTest.java`
- `backend/src/test/resources/application.properties`
- `docker-compose.yml`
- `docs/GMAIL_SETUP.md`
- `frontend/src/app/admin/admin.component.spec.ts`
- `frontend/src/app/admin/admin.guard.ts`
- `frontend/src/app/app.component.css`
- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.spec.ts`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/core/interceptors/auth.interceptor.ts`
- `frontend/src/app/core/models/api.models.ts`
- `frontend/src/app/core/services/api.service.ts`
- `frontend/src/app/core/services/auth.service.ts`
- `frontend/src/app/core/services/planning-http.spec.ts`
- `frontend/src/app/landing/landing.component.html`
- `frontend/src/app/landing/landing.component.spec.ts`
- `frontend/src/app/premium.test-fixtures.ts`

Este inventario es también un archivo nuevo. Se excluyen los archivos ajenos preexistentes de .qa/ y el nombre suelto de diseño. Los artefactos ignorados de target/ y dist/ no forman parte del código versionable.
