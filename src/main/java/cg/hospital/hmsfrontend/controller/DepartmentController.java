package cg.hospital.hmsfrontend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Controller
@RequestMapping("/department")
public class DepartmentController {

    @Autowired private RestTemplate restTemplate;
    @Value("${backend.base.url}") private String backendUrl;

    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String name,
                       Model model) {
        try {
            List<Map> list;
            int totalPages = 1;

            if (name != null && !name.isBlank()) {
                // findByName returns Optional — a single object, not a collection
                try {
                    ResponseEntity<Map> res = restTemplate.getForEntity(
                        backendUrl + "/departments/search/findByName?name=" + name, Map.class);
                    if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                        list = new ArrayList<>();
                        list.add(res.getBody());
                    } else {
                        list = new ArrayList<>();
                    }
                } catch (Exception ex) {
                    list = new ArrayList<>();
                }
                model.addAttribute("searchName", name);
            } else {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/departments?page=" + page + "&size=" + size, Map.class);
                Map body = res.getBody();
                Map emb = body != null ? (Map) body.get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("departments") : new ArrayList<>();
                Map pageInfo = body != null ? (Map) body.get("page") : null;
                totalPages = pageInfo != null ? ((Number) pageInfo.get("totalPages")).intValue() : 1;
            }

            // For each department, extract head physician name
            for (Map d : list) {
                Object headObj = d.get("head");
                if (headObj instanceof Map) {
                    Map headMap = (Map) headObj;
                    d.put("headName", headMap.get("name"));
                    d.put("headId", headMap.get("employeeId"));
                }
            }

            model.addAttribute("items", list);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("size", size);
        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", "Backend Error: " + e.getMessage());
        }
        return "department/list";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("departmentID", Integer.parseInt(params.get("departmentID")));
            body.put("name", params.get("name"));
            // Spring Data REST expects URI link for FK
            body.put("head", backendUrl + "/physicians/" + params.get("headId"));
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            String deptId = params.get("departmentID");
            try {
                restTemplate.getForEntity(backendUrl + "/departments/" + deptId, Map.class);
                restTemplate.exchange(backendUrl + "/departments/" + deptId, HttpMethod.PUT, req, Map.class);
            } catch (Exception e) {
                restTemplate.exchange(backendUrl + "/departments", HttpMethod.POST, req, Map.class);
            }
        } catch (Exception e) { /* ignore */ }
        return "redirect:/department/list";
    }

    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {
        try {
            ResponseEntity<Map> allRes = restTemplate.getForEntity(backendUrl + "/departments?size=50", Map.class);
            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> allDepts = allEmb != null ? (List<Map>) allEmb.get("departments") : new ArrayList<>();
            // Extract head names for dropdown display
            for (Map d : allDepts) {
                Object h = d.get("head");
                if (h instanceof Map) d.put("headName", ((Map)h).get("name"));
            }
            model.addAttribute("allItems", allDepts);
            model.addAttribute("selectedId", id);

            // Department detail
            try {
                ResponseEntity<Map> dRes = restTemplate.getForEntity(backendUrl + "/departments/" + id, Map.class);
                Map dept = dRes.getBody();
                if (dept != null && dept.get("head") instanceof Map) {
                    dept.put("headName", ((Map) dept.get("head")).get("name"));
                    dept.put("headId", ((Map) dept.get("head")).get("employeeId"));
                }
                model.addAttribute("selectedItem", dept);
            } catch (Exception e) { model.addAttribute("selectedItem", null); }

            // Affiliated physicians — correct param: departmentID
            try {
                ResponseEntity<Map> afRes = restTemplate.getForEntity(
                    backendUrl + "/affiliated-with/search/findByDepartment_DepartmentID?departmentID=" + id, Map.class);
                Map afEmb = afRes.getBody() != null ? (Map) afRes.getBody().get("_embedded") : null;
                // correct embedded key is "affiliatedWith"
                model.addAttribute("affiliatedList", afEmb != null ? (List<Map>) afEmb.get("affiliatedWith") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("affiliatedList", new ArrayList<>()); }

            // Primary affiliated physicians only
            try {
                ResponseEntity<Map> primRes = restTemplate.getForEntity(
                    backendUrl + "/affiliated-with/search/findByDepartment_DepartmentIDAndPrimaryAffiliationTrue?departmentID=" + id, Map.class);
                Map primEmb = primRes.getBody() != null ? (Map) primRes.getBody().get("_embedded") : null;
                model.addAttribute("primaryList", primEmb != null ? (List<Map>) primEmb.get("affiliatedWith") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("primaryList", new ArrayList<>()); }

        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        return "department/relations";
    }
}
