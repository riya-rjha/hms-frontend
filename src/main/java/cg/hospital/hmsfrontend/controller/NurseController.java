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

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(Model model) {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(
                    backendUrl + "/nurse?size=100", Map.class);

            Map emb = res.getBody() != null ? (Map) res.getBody().get("_embedded") : null;
            List<Map> list = emb != null ? (List<Map>) emb.get("nurses") : new ArrayList<>();

            model.addAttribute("items", list);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", e.getMessage());
        }

        return "nurse/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "101") int id, Model model) {

        try {
            // 🔹 ALL NURSES
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/nurse?size=100", Map.class);

            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> all = allEmb != null ? (List<Map>) allEmb.get("nurses") : new ArrayList<>();

            model.addAttribute("allItems", all);
            model.addAttribute("selectedId", id);

            // 🔹 SELECTED NURSE
            Map nurse = restTemplate.getForObject(
                    backendUrl + "/nurse/" + id, Map.class);

            model.addAttribute("selectedItem", nurse);

            // =====================================================
            // 🔥 ON_CALL (FETCH ALL + FILTER)
            // =====================================================
            List<Map> onCallList = new ArrayList<>();

            try {
                ResponseEntity<Map> ocRes = restTemplate.getForEntity(
                        backendUrl + "/on_call?size=100", Map.class);

                Map ocEmb = ocRes.getBody() != null ? (Map) ocRes.getBody().get("_embedded") : null;
                List<Map> allOc = ocEmb != null ? (List<Map>) ocEmb.get("onCalls") : new ArrayList<>();

                for (Map oc : allOc) {
                    try {
                        String href = (String) ((Map)((Map) oc.get("_links")).get("nurse")).get("href");

                        // remove {?projection}
                        href = href.replaceAll("\\{.*\\}", "");

                        int nurseId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        if (nurseId == id) {
                            onCallList.add(oc);
                        }

                    } catch (Exception ignored) {}
                }

            } catch (Exception ignored) {}

            model.addAttribute("onCallList", onCallList);

            // =====================================================
            // 🔥 UNDERGOES (FETCH ALL + FILTER + ENRICH)
            // =====================================================
            List<Map> undergoesList = new ArrayList<>();

            try {
                ResponseEntity<Map> ugRes = restTemplate.getForEntity(
                        backendUrl + "/undergoes?size=100", Map.class);

                Map ugEmb = ugRes.getBody() != null ? (Map) ugRes.getBody().get("_embedded") : null;
                List<Map> allUg = ugEmb != null ? (List<Map>) ugEmb.get("undergoes") : new ArrayList<>();

                for (Map u : allUg) {
                    try {
                        String href = (String) ((Map)((Map) u.get("_links")).get("assistingNurse")).get("href");
                        href = href.replaceAll("\\{.*\\}", "");

                        int nurseId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        if (nurseId == id) {

                            // 🔹 PATIENT NAME
                            try {
                                String pHref = (String) ((Map)((Map) u.get("_links")).get("patient")).get("href");
                                pHref = pHref.replaceAll("\\{.*\\}", "");

                                Map pat = restTemplate.getForObject(pHref, Map.class);
                                u.put("patientName", pat != null ? pat.get("name") : null);

                            } catch (Exception ignored) {}

                            // 🔹 PHYSICIAN NAME
                            try {
                                String phHref = (String) ((Map)((Map) u.get("_links")).get("physician")).get("href");
                                phHref = phHref.replaceAll("\\{.*\\}", "");

                                Map phy = restTemplate.getForObject(phHref, Map.class);
                                u.put("physicianName", phy != null ? phy.get("name") : null);

                            } catch (Exception ignored) {}

                            undergoesList.add(u);
                        }

                    } catch (Exception ignored) {}
                }

            } catch (Exception ignored) {}

            model.addAttribute("undergoesList", undergoesList);

            // =====================================================
            // 🔥 APPOINTMENTS (FETCH ALL + FILTER + ENRICH)
            // =====================================================
            List<Map> apptList = new ArrayList<>();

            try {
                ResponseEntity<Map> apptRes = restTemplate.getForEntity(
                        backendUrl + "/appointments?size=100", Map.class);

                Map apptEmb = apptRes.getBody() != null ? (Map) apptRes.getBody().get("_embedded") : null;
                List<Map> allAppt = apptEmb != null ? (List<Map>) apptEmb.get("appointments") : new ArrayList<>();

                for (Map a : allAppt) {
                    try {
                        String href = (String) ((Map)((Map) a.get("_links")).get("prepNurse")).get("href");
                        href = href.replaceAll("\\{.*\\}", "");

                        int nurseId = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));

                        if (nurseId == id) {

                            // 🔹 PATIENT NAME
                            try {
                                String pHref = (String) ((Map)((Map) a.get("_links")).get("patient")).get("href");
                                pHref = pHref.replaceAll("\\{.*\\}", "");

                                Map pat = restTemplate.getForObject(pHref, Map.class);
                                a.put("patientName", pat != null ? pat.get("name") : null);

                            } catch (Exception ignored) {}

                            // 🔹 PHYSICIAN NAME
                            try {
                                String phHref = (String) ((Map)((Map) a.get("_links")).get("physician")).get("href");
                                phHref = phHref.replaceAll("\\{.*\\}", "");

                                Map phy = restTemplate.getForObject(phHref, Map.class);
                                a.put("physicianName", phy != null ? phy.get("name") : null);

                            } catch (Exception ignored) {}

                            apptList.add(a);
                        }

                    } catch (Exception ignored) {}
                }

            } catch (Exception ignored) {}

            model.addAttribute("apptList", apptList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "nurse/relations";
    }
}