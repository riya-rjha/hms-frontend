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

    @Autowired
    private RestTemplate restTemplate;

    @Value("${backend.base.url}")
    private String backendUrl;

    // ===================== EXTRACT ID =====================
    private void enrichNurseIds(List<Map> list) {
        for (Map n : list) {
            if (n.get("employeeId") == null) {
                try {
                    String href = (String) ((Map)((Map) n.get("_links")).get("self")).get("href");
                    href = href.replaceAll("\\{.*\\}", "");
                    int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
                    n.put("employeeId", id);
                } catch (Exception ignored) {}
            }
        }
    }

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(Model model) {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/nurse?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> list = emb != null ? (List<Map>) emb.get("nurses") : new ArrayList<>();

            enrichNurseIds(list);

            model.addAttribute("items", list);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", e.getMessage());
        }

        return "nurse/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam int id, Model model) {

        try {
            // 🔹 ALL NURSES
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/nurse?size=100", Map.class);

            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> all = allEmb != null ? (List<Map>) allEmb.get("nurses") : new ArrayList<>();

            enrichNurseIds(all);

            model.addAttribute("allItems", all);
            model.addAttribute("selectedId", id);

            // 🔹 SELECTED
            Map nurse = restTemplate.getForObject(
                    backendUrl + "/nurse/" + id, Map.class);

            model.addAttribute("selectedItem", nurse);

            // =====================================================
            // 🔥 UNDERGOES — use search endpoint + direct fields
            // =====================================================
            List<Map> undergoesList = new ArrayList<>();

            try {
                ResponseEntity<Map> ugRes = restTemplate.getForEntity(
                        backendUrl + "/undergoes/search/findByAssistingNurse_EmployeeId?employeeId=" + id + "&size=100",
                        Map.class);

                Map ugEmb = ugRes.getBody() != null ? (Map) ugRes.getBody().get("_embedded") : null;
                List<Map> allUg = ugEmb != null ? (List<Map>) ugEmb.get("undergoes") : new ArrayList<>();

                for (Map u : allUg) {
                    try {
                        // 🔹 Patient name — use direct patientId field
                        Number patientId = (Number) u.get("patientId");
                        if (patientId != null) {
                            Map pat = restTemplate.getForObject(
                                    backendUrl + "/patients/" + patientId, Map.class);
                            u.put("patientName", pat != null ? pat.get("name") : patientId);
                        }
                    } catch (Exception ignored) {}

                    try {
                        // 🔹 Physician name — use direct physicianId field
                        Number phyId = (Number) u.get("physicianId");
                        if (phyId != null) {
                            Map phy = restTemplate.getForObject(
                                    backendUrl + "/physicians/" + phyId, Map.class);
                            u.put("physicianName", phy != null ? phy.get("name") : phyId);
                        }
                    } catch (Exception ignored) {}

                    undergoesList.add(u);
                }

            } catch (Exception ignored) {}

            model.addAttribute("undergoesList", undergoesList);

            // =====================================================
            // 🔥 ON CALL — parse nurse ID from composite self href
            // Format: {blockFloor}_{nurseId}_{blockCode}_{start}_{end}
            // =====================================================
            List<Map> onCallList = new ArrayList<>();

            try {
                ResponseEntity<Map> ocRes = restTemplate.getForEntity(
                        backendUrl + "/on_call?size=100", Map.class);

                Map ocEmb = ocRes.getBody() != null ? (Map) ocRes.getBody().get("_embedded") : null;
                List<Map> allOc = ocEmb != null ? (List<Map>) ocEmb.get("onCalls") : new ArrayList<>();

                for (Map oc : allOc) {
                    try {
                        String href = (String) ((Map)((Map) oc.get("_links")).get("self")).get("href");
                        href = href.replaceAll("\\{.*\\}", "");
                        String key = href.substring(href.lastIndexOf("/") + 1);

                        // composite key: blockFloor_nurseId_blockCode_...
                        String[] parts = key.split("_");
                        int nurseId = Integer.parseInt(parts[1]);

                        if (nurseId == id) {
                            onCallList.add(oc);
                        }

                    } catch (Exception ignored) {}
                }

            } catch (Exception ignored) {}

            model.addAttribute("onCallList", onCallList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "nurse/relations";
    }
}