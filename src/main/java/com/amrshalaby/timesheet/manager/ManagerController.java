package com.amrshalaby.timesheet.manager;
import io.micronaut.http.annotation.Controller; import io.micronaut.http.annotation.Get; import io.micronaut.security.annotation.Secured; import io.micronaut.views.View; import java.util.Map;
@Controller("/manager") @Secured({"MANAGER","ADMIN"}) public class ManagerController { @Get @View("dashboard") public Map<String,Object> dashboard(){ return Map.of("title","Manager dashboard","message","Submitted timesheets awaiting approval will appear here."); } }
