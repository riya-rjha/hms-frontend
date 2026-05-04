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
@RequestMapping("/appointment")
public class AppointmentController {

    @Autowired private RestTemplate restTemplate;
    @Value("${backend.base.url}") private String backendUrl;

    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) Integer physician,
                       @RequestParam(required = false) Integer patient,
                       @RequestParam(required = false) Integer nurse,
                       Model model) {
        try {
            List<Map> list;
            int totalPages = 1;

            if (physician != null) {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/appointments/search/findByPhysicianEntityEmployeeId?physician=" + physician + "&size=50", Map.class);
                Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("appointments") : new ArrayList<>();
                model.addAttribute("filterPhysician", physician);
            } else if (patient != null) {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/appointments/search/findByPatientEntitySsn?patient=" + patient + "&size=50", Map.class);
                Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("appointments") : new ArrayList<>();
                model.addAttribute("filterPatient", patient);
            } else if (nurse != null) {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/appointments/search/findByPrepNurseEntityEmployeeId?nurse=" + nurse + "&size=50", Map.class);
                Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("appointments") : new ArrayList<>();
                model.addAttribute("filterNurse", nurse);
            } else {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/appointments/search/findByOrderByStartoDesc?page=" + page + "&size=" + size, Map.class);
                Map body = res.getBody();
                Map emb = body != null ? (Map) body.get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("appointments") : new ArrayList<>();
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
        return "appointment/list";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            // Spring Data REST: use URI links for FK relations
            body.put("patientEntity", backendUrl + "/patients/" + params.get("patientSsn"));
            body.put("physicianEntity", backendUrl + "/physicians/" + params.get("physicianId"));
            if (params.get("nurseId") != null && !params.get("nurseId").isBlank()) {
                body.put("prepNurseEntity", backendUrl + "/nurse/" + params.get("nurseId"));
            }
            body.put("starto", params.get("starto"));
            body.put("endo", params.get("endo"));
            body.put("examinationRoom", params.get("examinationRoom"));
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            restTemplate.exchange(backendUrl + "/appointments", HttpMethod.POST, req, Map.class);
        } catch (Exception e) { /* ignore */ }
        return "redirect:/appointment/list";
    }

    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {
        try {
            // 🔹 All appointments
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                backendUrl + "/appointments/search/findByOrderByStartoDesc?size=50", Map.class);

            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> all = allEmb != null ? (List<Map>) allEmb.get("appointments") : new ArrayList<>();

            model.addAttribute("allItems", all);
            model.addAttribute("selectedId", id);

            // 🔹 Selected appointment
            Map selected = null;
            try {
                selected = restTemplate.getForObject(
                    backendUrl + "/appointments/" + id, Map.class);
            } catch (Exception ignored) {}

            model.addAttribute("selectedItem", selected);

            // 🔥 FULL PRESCRIPTION RESOLUTION
            ResponseEntity<Map> prescRes = restTemplate.getForEntity(
                backendUrl + "/prescriptions?size=100", Map.class);

            Map prescEmb = prescRes.getBody() != null ? (Map) prescRes.getBody().get("_embedded") : null;
            List<Map> allPresc = prescEmb != null ? (List<Map>) prescEmb.get("prescriptions") : new ArrayList<>();

            List<Map> prescList = new ArrayList<>();

            for (Map rx : allPresc) {
                try {
                    Map links = (Map) rx.get("_links");

                    // 🔹 Filter by appointment
                    Map apptLink = (Map) links.get("appointment");
                    if (apptLink == null) continue;

                    String aHref = (String) ((Map) apptLink).get("href");
                    int apptId = Integer.parseInt(aHref.substring(aHref.lastIndexOf("/") + 1));

                    if (apptId != id) continue;

                    // 🔹 Patient
                    Map patLink = (Map) links.get("patient");
                    if (patLink != null) {
                        String pHref = (String) ((Map) patLink).get("href");
                        int pId = Integer.parseInt(pHref.substring(pHref.lastIndexOf("/") + 1));

                        Map p = restTemplate.getForObject(
                            backendUrl + "/patients/" + pId, Map.class);

                        rx.put("patientName", p != null ? p.get("name") : pId);
                        rx.put("patientSsn", pId);
                    }

                    // 🔹 Physician
                    Map phyLink = (Map) links.get("physician");
                    if (phyLink != null) {
                        String phHref = (String) ((Map) phyLink).get("href");
                        int phId = Integer.parseInt(phHref.substring(phHref.lastIndexOf("/") + 1));

                        Map phy = restTemplate.getForObject(
                            backendUrl + "/physicians/" + phId, Map.class);

                        rx.put("physicianName", phy != null ? phy.get("name") : phId);
                        rx.put("physicianId", phId);
                    }

                    // 🔹 Medication
                    Map medLink = (Map) links.get("medication");
                    if (medLink != null) {
                        String mHref = (String) ((Map) medLink).get("href");
                        int mId = Integer.parseInt(mHref.substring(mHref.lastIndexOf("/") + 1));

                        Map med = restTemplate.getForObject(
                            backendUrl + "/procedures/" + mId, Map.class);

                        rx.put("medicationName", med != null ? med.get("name") : mId);
                    }

                    prescList.add(rx);

                } catch (Exception ignored) {}
            }

            model.addAttribute("prescList", prescList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "appointment/relations";
    }
}
