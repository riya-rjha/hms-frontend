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
@RequestMapping("/physician")
public class PhysicianController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${backend.base.url}")
    private String backendUrl;

    // ===================== UTILITY =====================
    private void enrichPhysicianIds(List<Map> list) {
        for (Map p : list) {
            if (p.get("employeeId") == null) {
                try {
                    Map links = (Map) p.get("_links");
                    Map self = (Map) links.get("self");
                    String href = (String) self.get("href");
                    int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
                    p.put("employeeId", id);
                } catch (Exception ignored) {}
            }
        }
    }

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(Model model) {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/physicians?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> list = emb != null ? (List<Map>) emb.get("physicians") : new ArrayList<>();

            enrichPhysicianIds(list);
            model.addAttribute("items", list);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", e.getMessage());
        }

        return "physician/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {

        try {
            // 🔹 All physicians
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/physicians?size=100", Map.class);

            Map allEmb = (Map) allRes.getBody().get("_embedded");
            List<Map> allPhysicians = (List<Map>) allEmb.get("physicians");

            enrichPhysicianIds(allPhysicians);

            model.addAttribute("allItems", allPhysicians);
            model.addAttribute("selectedId", id);

            // 🔹 Selected physician
            Map selected = restTemplate.getForObject(
                    backendUrl + "/physicians/" + id, Map.class);

            if (selected != null && selected.get("employeeId") == null) {
                try {
                    Map links = (Map) selected.get("_links");
                    Map self = (Map) links.get("self");
                    String href = (String) self.get("href");
                    int extractedId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
                    selected.put("employeeId", extractedId);
                } catch (Exception ignored) {}
            }

            model.addAttribute("selectedItem", selected);

            // 🔹 Patients under this physician
            ResponseEntity<Map> patRes = restTemplate.getForEntity(
                    backendUrl + "/patients/search/findByPcp_EmployeeId?employeeId=" + id,
                    Map.class);

            Map patEmb = patRes.getBody() != null ? (Map) patRes.getBody().get("_embedded") : null;
            List<Map> patList = patEmb != null ? (List<Map>) patEmb.get("patients") : new ArrayList<>();

            model.addAttribute("patList", patList);

            // ✅ FIXED: Use search endpoint directly (NO manual filtering)
            try {
                ResponseEntity<Map> apptRes = restTemplate.getForEntity(
                        backendUrl + "/appointments/search/findByPhysicianEntityEmployeeId?physician=" + id + "&size=20",
                        Map.class);

                Map apptEmb = apptRes.getBody() != null ? (Map) apptRes.getBody().get("_embedded") : null;
                List<Map> apptList = apptEmb != null ? (List<Map>) apptEmb.get("appointments") : new ArrayList<>();

                model.addAttribute("apptList", apptList);

            } catch (Exception e) {
                model.addAttribute("apptList", new ArrayList<>());
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "physician/relations";
    }
}