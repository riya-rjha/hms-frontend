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

    // 🔥 Utility: Extract employeeId from _links
    private void enrichPhysicianIds(List<Map> list) {
        for (Map p : list) {
            if (p.get("employeeId") == null) {
                try {
                    Map links = (Map) p.get("_links");
                    Map self = (Map) links.get("self");
                    String href = (String) self.get("href");
                    String idStr = href.substring(href.lastIndexOf("/") + 1);
                    p.put("employeeId", Integer.parseInt(idStr));
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

            // 🔥 Fix ID extraction
            enrichPhysicianIds(list);

            model.addAttribute("items", list);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", "Backend Error: " + e.getMessage());
        }

        return "physician/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {

        try {
            // 🔹 All physicians (dropdown)
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/physicians?size=100", Map.class);

            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> allPhysicians = allEmb != null ? (List<Map>) allEmb.get("physicians") : new ArrayList<>();

            enrichPhysicianIds(allPhysicians);

            model.addAttribute("allItems", allPhysicians);
            model.addAttribute("selectedId", id);

            // 🔹 Selected physician details
            Map selected = restTemplate.getForObject(
                    backendUrl + "/physicians/" + id, Map.class);

            model.addAttribute("selectedItem", selected);

            // 🔹 Patients under this physician
            ResponseEntity<Map> patRes = restTemplate.getForEntity(
                    backendUrl + "/patients/search/findByPcp_EmployeeId?employeeId=" + id,
                    Map.class);

            Map patEmb = patRes.getBody() != null ? (Map) patRes.getBody().get("_embedded") : null;
            List<Map> patList = patEmb != null ? (List<Map>) patEmb.get("patients") : new ArrayList<>();

            model.addAttribute("patList", patList);

            // 🔥 PRESCRIPTIONS (NEW FIX)
            ResponseEntity<Map> prescRes = restTemplate.getForEntity(
                    backendUrl + "/prescriptions/search/findByPhysician?physician=" + id,
                    Map.class);

            Map prescEmb = prescRes.getBody() != null ? (Map) prescRes.getBody().get("_embedded") : null;
            List<Map> prescList = prescEmb != null ? (List<Map>) prescEmb.get("prescriptions") : new ArrayList<>();

            model.addAttribute("prescList", prescList);

        } catch (Exception e) {
            model.addAttribute("error", "Error: " + e.getMessage());
        }

        return "physician/relations";
    }
}