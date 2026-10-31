package com.finora.app.admin;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.util.List;
import static com.finora.app.admin.AdminDtos.*;
@RestController @RequestMapping({"/api/admin","/api/v1/admin"}) @PreAuthorize("hasRole('ADMIN')")
public class AdminController {
 private final AdminService service;public AdminController(AdminService s){service=s;}
 @GetMapping("/categories") public PageResult<CategoryView> categories(@RequestParam(required=false)String q,@RequestParam(required=false)Boolean active,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.categories(q,active,page,size);}
 @GetMapping("/category-options") public List<ManagedCategory> options(){return service.options();}
 @PostMapping("/categories") public ManagedCategory create(Authentication a,@RequestBody CategoryInput r){return service.saveCategory((Long)a.getPrincipal(),null,r);}
 @PutMapping("/categories/{id}") public ManagedCategory update(Authentication a,@PathVariable Long id,@RequestBody CategoryInput r){return service.saveCategory((Long)a.getPrincipal(),id,r);}
 @GetMapping("/transactions") public TransactionsPage transactions(@RequestParam(required=false)String q,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(required=false)Long categoryId,@RequestParam(required=false)String reviewStatus,@RequestParam(required=false)Long userId,@RequestParam(defaultValue="false")boolean uncategorized,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.transactions(new Filters(q,from,to,categoryId,reviewStatus,userId,uncategorized),page,size);}
 @PostMapping("/corrections") public BatchView correct(Authentication a,@RequestBody Correction r){return service.correct((Long)a.getPrincipal(),r);}
 @GetMapping("/rules") public PageResult<RuleView> rules(@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.rules(q,page,size);}
 @PostMapping("/rules") public RuleView createRule(Authentication a,@RequestBody RuleInput r){return service.saveRule((Long)a.getPrincipal(),null,r);}
 @PutMapping("/rules/{id}") public RuleView updateRule(Authentication a,@PathVariable Long id,@RequestBody RuleInput r){return service.saveRule((Long)a.getPrincipal(),id,r);}
 @PostMapping("/rules/{id}/preview") public BatchView preview(Authentication a,@PathVariable Long id,@RequestBody(required=false)Filters f){return service.previewRule((Long)a.getPrincipal(),id,f);}
 @PostMapping("/categories/{id}/reassign-preview") public BatchView reassign(Authentication a,@PathVariable Long id,@RequestBody Reassign r){return service.previewReassign((Long)a.getPrincipal(),id,r);}
 @GetMapping("/batches") public PageResult<ClassificationBatch> batches(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.batches(page,size);}
 @GetMapping("/batches/{id}") public BatchView batch(Authentication a,@PathVariable String id){return service.batch((Long)a.getPrincipal(),id);}
 @PostMapping("/batches/{id}/apply") public BatchView apply(Authentication a,@PathVariable String id,@RequestBody Confirmation c){return service.apply((Long)a.getPrincipal(),id,c);}
 @PostMapping("/batches/{id}/revert") public BatchView revert(Authentication a,@PathVariable String id,@RequestBody Confirmation c){return service.revert((Long)a.getPrincipal(),id,c);}
 @GetMapping("/history") public PageResult<AdminAudit> history(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.history(page,size);}
}
