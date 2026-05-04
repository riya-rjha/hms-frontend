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
            // 🔹 ALL PHYSICIANS
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/physicians?size=100", Map.class);

            Map allEmb = (Map) allRes.getBody().get("_embedded");
            List<Map> allPhysicians = (List<Map>) allEmb.get("physicians");

            enrichPhysicianIds(allPhysicians);

            model.addAttribute("allItems", allPhysicians);
            model.addAttribute("selectedId", id);

            // 🔹 SELECTED PHYSICIAN
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

            // 🔹 PATIENTS UNDER THIS PHYSICIAN
            ResponseEntity<Map> patRes = restTemplate.getForEntity(
                    backendUrl + "/patients/search/findByPcp_EmployeeId?employeeId=" + id,
                    Map.class);

            Map patEmb = patRes.getBody() != null ? (Map) patRes.getBody().get("_embedded") : null;
            List<Map> patList = patEmb != null ? (List<Map>) patEmb.get("patients") : new ArrayList<>();

            model.addAttribute("patList", patList);

            // 🔥 FULL PRESCRIPTION LOGIC
            ResponseEntity<Map> prescRes = restTemplate.getForEntity(
                    backendUrl + "/prescriptions?size=100", Map.class);

            Map prescEmb = prescRes.getBody() != null ? (Map) prescRes.getBody().get("_embedded") : null;
            List<Map> allPresc = prescEmb != null ? (List<Map>) prescEmb.get("prescriptions") : new ArrayList<>();

            List<Map> prescList = new ArrayList<>();

            for (Map rx : allPresc) {
                try {
                    Map links = (Map) rx.get("_links");

                    // 🔹 Extract physician ID
                    Map phyLink = (Map) links.get("physician");
                    String phyHref = (String) ((Map) phyLink).get("href");
                    int phyId = Integer.parseInt(phyHref.substring(phyHref.lastIndexOf("/") + 1));

                    if (phyId != id) continue;

                    // 🔹 Extract patient
                    Map patLink = (Map) links.get("patient");
                    String patHref = (String) ((Map) patLink).get("href");
                    int patientId = Integer.parseInt(patHref.substring(patHref.lastIndexOf("/") + 1));

                    Map patient = restTemplate.getForObject(
                            backendUrl + "/patients/" + patientId, Map.class);

                    rx.put("patientName", patient != null ? patient.get("name") : patientId);

                    // 🔹 Extract medication
                    Map medLink = (Map) links.get("medication");
                    String medHref = (String) ((Map) medLink).get("href");
                    int medId = Integer.parseInt(medHref.substring(medHref.lastIndexOf("/") + 1));

                    Map med = restTemplate.getForObject(
                            backendUrl + "/procedures/" + medId, Map.class);

                    rx.put("medicationName", med != null ? med.get("name") : medId);

                    prescList.add(rx);

                } catch (Exception ignored) {}
            }

            model.addAttribute("prescList", prescList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "physician/relations";
    }
}