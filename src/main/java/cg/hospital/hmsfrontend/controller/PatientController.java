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
@RequestMapping("/patient")
public class PatientController {

    @Autowired private RestTemplate restTemplate;

    @Value("${backend.base.url}")
    private String backendUrl;

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(Model model) {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/patients?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> list = emb != null ? (List<Map>) emb.get("patients") : new ArrayList<>();

            for (Map p : list) {

                // ✅ SSN FIX
                if (p.get("ssn") == null) {
                    try {
                        String href = (String) ((Map)((Map) p.get("_links")).get("self")).get("href");
                        href = href.replaceAll("\\{.*\\}", "").trim();
                        p.put("ssn", Integer.parseInt(href.substring(href.lastIndexOf("/") + 1)));
                    } catch (Exception ignored) {}
                }

                // 🔥 PCP FIX (IMPORTANT)
                try {
                    String selfHref = (String) ((Map)((Map) p.get("_links")).get("self")).get("href");
                    selfHref = selfHref.replaceAll("\\{.*\\}", "").trim();

                    Map pcp = restTemplate.getForObject(selfHref + "/pcp", Map.class);

                    if (pcp != null) {
                        p.put("pcpName", pcp.get("name"));

                        String href = (String) ((Map)((Map) pcp.get("_links")).get("self")).get("href");
                        href = href.replaceAll("\\{.*\\}", "").trim();
                        int phyId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        p.put("pcpId", phyId);
                    }

                } catch (Exception ignored) {
                    p.put("pcpName", "—");
                    p.put("pcpId", "—");
                }
            }

            model.addAttribute("items", list);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", e.getMessage());
        }

        return "patient/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam int id, Model model) {

        try {
            // 🔹 All patients
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/patients?size=100", Map.class);

            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> all = allEmb != null ? (List<Map>) allEmb.get("patients") : new ArrayList<>();

            // ✅ SSN FIX (for dropdown)
            for (Map p : all) {
                if (p.get("ssn") == null) {
                    try {
                        String href = (String) ((Map)((Map) p.get("_links")).get("self")).get("href");
                        href = href.replaceAll("\\{.*\\}", "").trim();
                        p.put("ssn", Integer.parseInt(href.substring(href.lastIndexOf("/") + 1)));
                    } catch (Exception ignored) {}
                }
            }

            model.addAttribute("allItems", all);
            model.addAttribute("selectedId", id);

            // 🔹 Selected patient
            Map pat = restTemplate.getForObject(
                    backendUrl + "/patients/" + id, Map.class);

            // ✅ SSN FIX for selected
            if (pat != null && pat.get("ssn") == null) {
                try {
                    String href = (String) ((Map)((Map) pat.get("_links")).get("self")).get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    pat.put("ssn", Integer.parseInt(href.substring(href.lastIndexOf("/") + 1)));
                } catch (Exception ignored) {}
            }

            // ✅ PCP FIX — use proper endpoint
            try {
                Map pcp = restTemplate.getForObject(
                        backendUrl + "/patients/" + id + "/pcp",
                        Map.class);

                if (pcp != null) {
                    pat.put("pcpName", pcp.get("name"));

                    // extract physician ID from _links
                    String href = (String) ((Map)((Map) pcp.get("_links")).get("self")).get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    int phyId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                    pat.put("pcpId", phyId);
                }

            } catch (Exception ignored) {
                pat.put("pcpName", "—");
                pat.put("pcpId", "—");
            }

            model.addAttribute("selectedItem", pat);

            // ✅ APPOINTMENTS (correct endpoint)
            try {
                ResponseEntity<Map> apptRes = restTemplate.getForEntity(
                        backendUrl + "/appointments/search/findByPatientEntitySsn?patient=" + id + "&size=20",
                        Map.class);

                Map apptEmb = apptRes.getBody() != null ? (Map) apptRes.getBody().get("_embedded") : null;
                List<Map> apptList = apptEmb != null ? (List<Map>) apptEmb.get("appointments") : new ArrayList<>();

                model.addAttribute("apptList", apptList);

            } catch (Exception e) {
                model.addAttribute("apptList", new ArrayList<>());
            }

            // ✅ PRESCRIPTIONS + MEDICATION FIX
            try {
                ResponseEntity<Map> prescRes = restTemplate.getForEntity(
                        backendUrl + "/prescriptions/search/findByIdPatient?patient=" + id + "&size=20",
                        Map.class);

                Map prescEmb = prescRes.getBody() != null ? (Map) prescRes.getBody().get("_embedded") : null;
                List<Map> prescList = prescEmb != null ? (List<Map>) prescEmb.get("prescriptions") : new ArrayList<>();

                for (Map rx : prescList) {
                    try {
                        Map links = (Map) rx.get("_links");
                        if (links != null) {
                            Map medLink = (Map) links.get("medication");
                            if (medLink != null) {
                                String href = (String) medLink.get("href");

                                // 🔥 FIX: remove {?projection}
                                href = href.replaceAll("\\{.*\\}", "").trim();

                                int medId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
                                rx.put("medicationId", medId);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                model.addAttribute("prescList", prescList);

            } catch (Exception e) {
                model.addAttribute("prescList", new ArrayList<>());
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "patient/relations";
    }
}