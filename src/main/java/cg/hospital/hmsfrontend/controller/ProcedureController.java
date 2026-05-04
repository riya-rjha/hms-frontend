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
@RequestMapping("/procedure")
public class ProcedureController {

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
                // No server-side search for procedures — filter client-side
                ResponseEntity<Map> res = restTemplate.getForEntity(backendUrl + "/procedures?size=200", Map.class);
                Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
                List<Map> all = emb != null ? (List<Map>) emb.get("procedures") : new ArrayList<>();
                String lower = name.toLowerCase();
                list = all.stream().filter(p -> {
                    String n = p.get("name") != null ? p.get("name").toString().toLowerCase() : "";
                    return n.contains(lower);
                }).collect(java.util.stream.Collectors.toList());
                model.addAttribute("searchName", name);
            } else {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/procedures?page=" + page + "&size=" + size, Map.class);
                Map body = res.getBody();
                Map emb = body != null ? (Map) body.get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("procedures") : new ArrayList<>();
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
        return "procedure/list";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("code", Integer.parseInt(params.get("code")));
            body.put("name", params.get("name"));
            body.put("cost", Double.parseDouble(params.get("cost")));
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            String code = params.get("code");
            try {
                restTemplate.getForEntity(backendUrl + "/procedures/" + code, Map.class);
                restTemplate.exchange(backendUrl + "/procedures/" + code, HttpMethod.PUT, req, Map.class);
            } catch (Exception e) {
                restTemplate.exchange(backendUrl + "/procedures", HttpMethod.POST, req, Map.class);
            }
        } catch (Exception e) { /* ignore */ }
        return "redirect:/procedure/list";
    }

    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {
        try {
            ResponseEntity<Map> allRes = restTemplate.getForEntity(backendUrl + "/procedures?size=100", Map.class);
            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            model.addAttribute("allItems", allEmb != null ? (List<Map>) allEmb.get("procedures") : new ArrayList<>());
            model.addAttribute("selectedId", id);

            // Procedure detail
            try {
                model.addAttribute("selectedItem", restTemplate.getForEntity(backendUrl + "/procedures/" + id, Map.class).getBody());
            } catch (Exception e) { model.addAttribute("selectedItem", null); }

            // TrainedIn — physicians trained in this procedure (param: treatment)
            try {
                ResponseEntity<Map> tiRes = restTemplate.getForEntity(
                    backendUrl + "/trained-in/search/findByIdTreatment?treatment=" + id, Map.class);
                Map tiEmb = tiRes.getBody() != null ? (Map) tiRes.getBody().get("_embedded") : null;
                model.addAttribute("trainedList", tiEmb != null ? (List<Map>) tiEmb.get("trainedIn") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("trainedList", new ArrayList<>()); }

            // Undergoes — patients who underwent this procedure (param: procedures)
            try {
                ResponseEntity<Map> ugRes = restTemplate.getForEntity(
                    backendUrl + "/undergoes/search/findByProcedures?procedures=" + id, Map.class);
                Map ugEmb = ugRes.getBody() != null ? (Map) ugRes.getBody().get("_embedded") : null;
                model.addAttribute("undergoesList", ugEmb != null ? (List<Map>) ugEmb.get("undergoes") : new ArrayList<>());
            } catch (Exception e) { model.addAttribute("undergoesList", new ArrayList<>()); }

        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        return "procedure/relations";
    }
}
