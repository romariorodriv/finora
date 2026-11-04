package com.finora.app.gmail.controller;

import com.finora.app.gmail.dto.GmailDtos;
import com.finora.app.gmail.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping({"/api/integrations/gmail", "/api/v1/integrations/gmail"})
public class GmailController {
  private final GmailOAuthService oauth;
  private final com.finora.app.auth.google.AuthCookies cookies;
  private final String frontend;
  private final com.finora.app.gmail.repository.ImportedMessageRepository imported;
  private final com.finora.app.gmail.jobs.GmailJobStore jobs;

  public GmailController(GmailOAuthService oauth, com.finora.app.gmail.jobs.GmailJobStore jobs,com.finora.app.auth.google.AuthCookies cookies,@org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:4200}")String frontend,com.finora.app.gmail.repository.ImportedMessageRepository imported) {
    this.oauth = oauth;this.cookies=cookies;this.frontend=frontend.replaceAll("/+$","");this.imported=imported;
    this.jobs = jobs;
  }

  @GetMapping("/authorization-url")
  public GmailDtos.AuthorizationUrlResponse authorization(Authentication a,jakarta.servlet.http.HttpServletResponse response) {
    String browser=com.finora.app.auth.google.AuthSecrets.random();
    var result=oauth.authorizationUrl((Long) a.getPrincipal(),browser);
    cookies.set(response,com.finora.app.auth.google.AuthCookies.GMAIL_FLOW,browser,java.time.Duration.ofMinutes(10));
    response.setHeader("Cache-Control","no-store");
    return result;
  }

  @GetMapping("/callback")
  public RedirectView callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
    String target;
    try{target=oauth.callback(code,state,error,cookies.read(request,com.finora.app.auth.google.AuthCookies.GMAIL_FLOW));}
    catch(com.finora.app.shared.error.ApiException e){target=frontend+"/?gmail=failed";}
    cookies.clear(response,com.finora.app.auth.google.AuthCookies.GMAIL_FLOW);
    response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");
    RedirectView view = new RedirectView(target);
    view.setExposeModelAttributes(false);
    return view;
  }

  @GetMapping("/status")
  public GmailDtos.StatusResponse status(Authentication a) {
    return oauth.status((Long) a.getPrincipal());
  }

  @PostMapping("/sync")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public com.finora.app.gmail.jobs.GmailJobStore.Status sync(Authentication a) {
    return jobs.enqueue((Long) a.getPrincipal());
  }
  @GetMapping("/sync/status")
  public com.finora.app.gmail.jobs.GmailJobStore.Status syncStatus(Authentication a) {
    return jobs.status((Long) a.getPrincipal());
  }

  public record ReviewItem(Long id,String subject,String status,java.time.Instant receivedAt,String reason){}
  @GetMapping("/review")
  public java.util.Map<String,Object> review(Authentication authentication,@RequestParam(defaultValue="0")int page){
    if(page<0)throw new com.finora.app.shared.error.ApiException(400,"INVALID_PAGE","Página inválida");
    var rows=imported.findByUserIdAndProcessingStatusIn((Long)authentication.getPrincipal(),
      java.util.List.of(com.finora.app.gmail.entity.ImportedMessage.Status.REVIEW_REQUIRED,com.finora.app.gmail.entity.ImportedMessage.Status.IGNORED,com.finora.app.gmail.entity.ImportedMessage.Status.FAILED),
      org.springframework.data.domain.PageRequest.of(page,20,org.springframework.data.domain.Sort.by("id").descending()));
    return java.util.Map.of("items",rows.stream().map(m->new ReviewItem(m.id,m.subject,m.processingStatus.name(),m.receivedAt,m.errorCode)).toList(),"total",rows.getTotalElements(),"page",page,"totalPages",rows.getTotalPages());
  }
  @DeleteMapping
  public void disconnect(Authentication a) {
    oauth.disconnect((Long) a.getPrincipal());
  }
}
