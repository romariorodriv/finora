package com.finora.app.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finora.app.gmail.jobs.GmailMessageProcessor;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GmailVideoFixturesTest {
  @Test void documentedFictionalFixturesMatchTheExistingParserWithoutRelaxingSenderRules() throws Exception {
    var parser=new PeruBankParser();
    try(var stream=getClass().getResourceAsStream("/gmail-video-fixtures.json")) {
      assertNotNull(stream);
      for(var fixture:new ObjectMapper().readTree(stream)) {
        String sender=fixture.path("sender").asText(),text=fixture.path("subject").asText()+" "+fixture.path("body").asText();
        String expected=fixture.path("expected").asText();
        if(expected.equals("UNSUPPORTED_SENDER_BODY_NOT_READ")) {
          assertFalse(GmailMessageProcessor.isRecognizedNotificationSender(sender));continue;
        }
        var parsed=parser.tryParse(sender,text);
        if(expected.equals("REVIEW_REQUIRED")||expected.equals("IGNORED")) {assertTrue(parsed.isEmpty(),fixture.path("id").asText());continue;}
        var p=parsed.orElseThrow();
        assertEquals(0,new BigDecimal(fixture.path("amount").asText()).compareTo(p.amount()));
        assertEquals(fixture.path("currency").asText(),p.currency());assertEquals(fixture.path("merchant").asText(),p.merchant());
        assertEquals(fixture.path("parserCategory").asText(),p.category());assertEquals(fixture.path("type").asText(),p.type().name());
        assertEquals(expected.equals("REJECTED")?BankEmailParser.OperationStatus.REJECTED:BankEmailParser.OperationStatus.COMPLETED,p.status());
      }
    }
  }
}
