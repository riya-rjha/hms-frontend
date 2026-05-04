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

    // 🔥 Extract PCP info (works for both object + _links)
    private void enrichPCP(List<Map> list) {
        for (Map p : list) {
            try {
                Object pcpObj = p.get("pcp");

                if (pcpObj instanceof Map) {
                    Map m = (Map) pcpObj;
                    p.put("pcpName", m.get("name"));
                    p.put("pcpId", m.get("employeeId"));
                } else {
                    Map links = (Map) p.get("_links");
                    Map pcpLink = (Map) links.get("pcp");

                    if (pcpLink != null) {
                        String href = (String) ((Map) pcpLink).get("href");
                        int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        p.put("pcpId", id);

                        Map phy = restTemplate.getForObject(
                                backendUrl + "/physicians/" + id, Map.class);

                        if (phy != null) {
                            p.put("pcpName", phy.get("name"));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(Model model) {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/patients?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> list = emb != null ? (List<Map>) emb.get("patients") : new ArrayList<>();

            enrichPCP(list);

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

            enrichPCP(all);

            model.addAttribute("allItems", all);
            model.addAttribute("selectedId", id);

            // 🔹 Selected patient
            Map pat = restTemplate.getForObject(
                    backendUrl + "/patients/" + id, Map.class);

            List<Map> single = new ArrayList<>();
            if (pat != null) single.add(pat);

            enrichPCP(single);

            model.addAttribute("selectedItem", pat);

            // 🔥 APPOINTMENTS (safe fallback)
            try {
                ResponseEntity<Map> apptRes = restTemplate.getForEntity(
                        backendUrl + "/appointments", Map.class);

                Map apptEmb = apptRes.getBody() != null ? (Map) apptRes.getBody().get("_embedded") : null;
                List<Map> allAppts = apptEmb != null ? (List<Map>) apptEmb.get("appointments") : new ArrayList<>();

                List<Map> apptList = new ArrayList<>();

                for (Map a : allAppts) {
                    try {
                        Number patientId = (Number) a.get("patient");
                        if (patientId != null && patientId.intValue() == id) {
                            apptList.add(a);
                        }
                    } catch (Exception ignored) {}
                }

                model.addAttribute("apptList", apptList);

            } catch (Exception e) {
                model.addAttribute("apptList", new ArrayList<>());
            }

            // 🔥 PRESCRIPTIONS (FIXED)
            ResponseEntity<Map> prescRes = restTemplate.getForEntity(
                    backendUrl + "/prescribes", Map.class);

            Map prescEmb = prescRes.getBody() != null ? (Map) prescRes.getBody().get("_embedded") : null;
            List<Map> allPresc = prescEmb != null ? (List<Map>) prescEmb.get("prescribes") : new ArrayList<>();

            List<Map> prescList = new ArrayList<>();

            for (Map rx : allPresc) {
                try {
                    Number patientId = (Number) rx.get("patient");
                    if (patientId != null && patientId.intValue() == id) {
                        prescList.add(rx);
                    }
                } catch (Exception ignored) {}
            }

            model.addAttribute("prescList", prescList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "patient/relations";
    }
}