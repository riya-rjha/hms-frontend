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

    @Value("${backend.base.url}")
    private String backendUrl;

    // ===================== COMMON ENRICH =====================
    private void enrichAppointments(List<Map> list) {

        for (Map a : list) {
            try {
                // 🔹 Appointment ID
                if (a.get("appointmentId") == null) {
                    String href = (String) ((Map)((Map) a.get("_links")).get("self")).get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
                    a.put("appointmentId", id);
                }

                Map links = (Map) a.get("_links");

                // 🔹 Patient
                Map patLink = (Map) links.get("patient");
                if (patLink != null) {
                    String href = (String) patLink.get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    int ssn = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                    Map p = restTemplate.getForObject(
                            backendUrl + "/patients/" + ssn, Map.class);

                    a.put("patientSsn", ssn);
                    a.put("patientName", p != null ? p.get("name") : ssn);
                }

                // 🔹 Physician
                Map phyLink = (Map) links.get("physician");
                if (phyLink != null) {
                    String href = (String) phyLink.get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                    Map phy = restTemplate.getForObject(
                            backendUrl + "/physicians/" + id, Map.class);

                    a.put("physicianId", id);
                    a.put("physicianName", phy != null ? phy.get("name") : id);
                }

                // 🔹 Nurse (nullable)
                Map nurseLink = (Map) links.get("prepNurse");
                if (nurseLink != null) {
                    String href = (String) nurseLink.get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                    Map n = restTemplate.getForObject(
                            backendUrl + "/nurses/" + id, Map.class);

                    a.put("prepNurseName", n != null ? n.get("name") : id);
                } else {
                    a.put("prepNurseName", "—");
                }

            } catch (Exception ignored) {}
        }
    }

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(Model model) {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/appointments?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> list = emb != null ? (List<Map>) emb.get("appointments") : new ArrayList<>();

            // ✅ FIX
            enrichAppointments(list);

            model.addAttribute("items", list);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", e.getMessage());
        }

        return "appointment/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {

        try {
            // 🔹 All appointments
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/appointments?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> all = emb != null ? (List<Map>) emb.get("appointments") : new ArrayList<>();

            enrichAppointments(all);

            model.addAttribute("allItems", all);
            model.addAttribute("selectedId", id);

            // 🔹 Selected appointment
            Map selected = restTemplate.getForObject(
                    backendUrl + "/appointments/" + id, Map.class);

            model.addAttribute("selectedItem", selected);

            // ===================== ✅ FIXED PRESCRIPTIONS =====================

            ResponseEntity<Map> prescRes = restTemplate.getForEntity(
                    backendUrl + "/prescriptions/search/findByAppointment_AppointmentId?id=" + id + "&size=100",
                    Map.class);

            Map prescEmb = prescRes.getBody() != null ? (Map) prescRes.getBody().get("_embedded") : null;
            List<Map> prescList = prescEmb != null ? (List<Map>) prescEmb.get("prescriptions") : new ArrayList<>();

            // 🔥 ENRICH EACH PRESCRIPTION
            for (Map rx : prescList) {
                try {
                    Map links = (Map) rx.get("_links");

                    // 🔹 Patient
                    Map patLink = (Map) links.get("patient");
                    if (patLink != null) {
                        String href = (String) patLink.get("href");
                        href = href.replaceAll("\\{.*\\}", "").trim();
                        int ssn = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        Map p = restTemplate.getForObject(
                                backendUrl + "/patients/" + ssn, Map.class);

                        rx.put("patientName", p != null ? p.get("name") : ssn);
                        rx.put("patientSsn", ssn);
                    }

                    // 🔹 Physician
                    Map phyLink = (Map) links.get("physician");
                    if (phyLink != null) {
                        String href = (String) phyLink.get("href");
                        href = href.replaceAll("\\{.*\\}", "").trim();
                        int pid = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        Map phy = restTemplate.getForObject(
                                backendUrl + "/physicians/" + pid, Map.class);

                        rx.put("physicianName", phy != null ? phy.get("name") : pid);
                        rx.put("physicianId", pid);
                    }

                    // 🔹 Medication
                    Map medLink = (Map) links.get("medication");
                    if (medLink != null) {
                        String href = (String) medLink.get("href");
                        href = href.replaceAll("\\{.*\\}", "").trim();
                        int mid = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        Map med = restTemplate.getForObject(
                                backendUrl + "/procedures/" + mid, Map.class);

                        rx.put("medicationName", med != null ? med.get("name") : mid);
                    }

                } catch (Exception ignored) {}
            }

            model.addAttribute("prescList", prescList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "appointment/relations";
    }
}