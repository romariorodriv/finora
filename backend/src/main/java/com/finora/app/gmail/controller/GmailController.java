package com.finora.app.gmail.controller;

import com.finora.app.gmail.dto.GmailDtos;
import com.finora.app.gmail.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController @RequestMapping("/api/integrations/gmail")
public class GmailController {
    private final GmailOAuthService oauth;private final GmailSyncService sync;
    public GmailController(GmailOAuthService oauth,GmailSyncService sync){this.oauth=oauth;this.sync=sync;}
    @GetMapping("/authorization-url") public GmailDtos.AuthorizationUrlResponse authorization(Authentication a){return oauth.authorizationUrl((Long)a.getPrincipal());}
    @GetMapping("/callback") public RedirectView callback(@RequestParam(required=false)String code,@RequestParam(required=false)String state,@RequestParam(required=false)String error){RedirectView v=new RedirectView(oauth.callback(code,state,error));v.setExposeModelAttributes(false);return v;}
    @GetMapping("/status") public GmailDtos.StatusResponse status(Authentication a){return oauth.status((Long)a.getPrincipal());}
    @PostMapping("/sync") public GmailDtos.SyncResponse sync(Authentication a){return sync.sync((Long)a.getPrincipal());}
    @DeleteMapping public void disconnect(Authentication a){oauth.disconnect((Long)a.getPrincipal());}
}
