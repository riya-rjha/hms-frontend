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
@RequestMapping("/nurse")
public class NurseController {

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

            // Backend path is /api/nurse (not /api/nurses)
            if (name != null && !name.isBlank()) {
                ResponseEntity<Map> res = restTemplate.getForEntity(backendUrl + "/nurse?size=200", Map.class);
                Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
                List<Map> all = emb != null ? (List<Map>) emb.get("nurses") : new ArrayList<>();
                String lower = name.toLowerCase();
                list = all.stream().filter(n -> {
                    String nm = n.get("name") != null ? n.get("name").toString().toLowerCase() : "";
                    return nm.contains(lower);
                }).collect(java.util.stream.Collectors.toList());
                model.addAttribute("searchName", name);
            } else {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/nurse?page=" + page + "&size=" + size, Map.class);
                Map body = res.getBody();
                Map emb = body != null ? (Map) body.get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("nurses") : new ArrayList<>();
                Map pageInfo = body != null ? (Map) body.get("page") : null;
                totalPages = pageInfo != null ? ((Number) pageInfo.get("totalPages")).intValue() : 1;
            }

            model.addAttribute("items", list);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("size", size);
        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", "Backend Error: " + e.getMessage());
        }
        return "nurse/list";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("employeeId", Integer.parseInt(params.get("employeeId")));
            body.put("name", params.get("name"));
            body.put("position", params.get("position"));
            body.put("registered", Boolean.parseBoolean(params.get("registered")));
            body.put("ssn", Integer.parseInt(params.get("ssn")));
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            String id = params.get("employeeId");
            // Backend path: /api/nurse (not /api/nurses)
            try {
                restTemplate.getForEntity(backendUrl + "/nurse/" + id, Map.class);
                restTemplate.exchange(backendUrl + "/nurse/" + id, HttpMethod.PUT, req, Map.class);
            } catch (Exception e) {
                restTemplate.exchange(backendUrl + "/nurse", HttpMethod.POST, req, Map.class);
            }
        } catch (Exception e) { /* ignore */ }
        return "redirect:/nurse/list";
    }

    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "101") int id, Model model) {
        try {
            ResponseEntity<Map> allRes = restTemplate.getForEntity(backendUrl + "/nurse?size=100", Map.class);
            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            model.addAttribute("allItems", allEmb != null ? (List<Map>) allEmb.get("nurses") : new ArrayList<>());
            model.addAttribute("selectedId", id);

            // Nurse detail
            try {
                model.addAttribute("selectedItem", restTemplate.getForEntity(backendUrl + "/nurse/" + id, Map.class).getBody());
            } catch (Exception e) { model.addAttribute("selectedItem", null); }

            // On-Call schedule — param is employeeID (capital D)
            try {
                ResponseEntity<Map> ocRes = restTemplate.getForEntity(
                    backendUrl + "/on_call/search/findByNurse_EmployeeId?employeeID=" + id, Map.class);
                Map ocEmb = ocRes.getBody() != null ? (Map) ocRes.getBody().get("_embedded") : null;
                model.addAttribute("onCallList", ocEmb != null ? (List<Map>) ocEmb.get("onCalls") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("onCallList", new ArrayList<>()); }

            // Undergoes (as assisting nurse) — param is employeeId
            try {
                ResponseEntity<Map> ugRes = restTemplate.getForEntity(
                    backendUrl + "/undergoes/search/findByAssistingNurse_EmployeeId?employeeId=" + id, Map.class);
                Map ugEmb = ugRes.getBody() != null ? (Map) ugRes.getBody().get("_embedded") : null;
                model.addAttribute("undergoesList", ugEmb != null ? (List<Map>) ugEmb.get("undergoes") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("undergoesList", new ArrayList<>()); }

            // Appointments where PrepNurse
            try {
                ResponseEntity<Map> apptRes = restTemplate.getForEntity(
                    backendUrl + "/appointments/search/findByPrepNurseEntityEmployeeId?nurse=" + id + "&size=20", Map.class);
                Map apptEmb = apptRes.getBody() != null ? (Map) apptRes.getBody().get("_embedded") : null;
                model.addAttribute("apptList", apptEmb != null ? (List<Map>) apptEmb.get("appointments") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("apptList", new ArrayList<>()); }

        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        return "nurse/relations";
    }
}
