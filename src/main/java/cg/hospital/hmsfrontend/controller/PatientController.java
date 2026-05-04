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
    @Value("${backend.base.url}") private String backendUrl;

    // 🔥 COMMON METHOD (IMPORTANT)
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

    @GetMapping("/list")
    public String list(Model model) {
        ResponseEntity<Map> res = restTemplate.getForEntity(
                backendUrl + "/patients?size=100", Map.class);

        Map emb = (Map) res.getBody().get("_embedded");
        List<Map> list = (List<Map>) emb.get("patients");

        enrichPCP(list);

        model.addAttribute("items", list);
        return "patient/list";
    }

    @GetMapping("/relations")
    public String relations(@RequestParam int id, Model model) {

        // all patients
        ResponseEntity<Map> allRes = restTemplate.getForEntity(
                backendUrl + "/patients?size=100", Map.class);

        List<Map> all = (List<Map>) ((Map) allRes.getBody().get("_embedded")).get("patients");
        enrichPCP(all);

        model.addAttribute("allItems", all);
        model.addAttribute("selectedId", id);

        // selected patient
        Map pat = restTemplate.getForObject(
                backendUrl + "/patients/" + id, Map.class);

        List<Map> single = new ArrayList<>();
        single.add(pat);
        enrichPCP(single);

        model.addAttribute("selectedItem", pat);

        // appointments
        try {
            Map appt = restTemplate.getForObject(
                    backendUrl + "/appointments/search/findByPatientEntitySsn?patient=" + id,
                    Map.class);

            model.addAttribute("apptList",
                    appt != null ? ((Map) appt.get("_embedded")).get("appointments") : new ArrayList<>());
        } catch (Exception e) {
            model.addAttribute("apptList", new ArrayList<>());
        }

        // prescriptions
        try {
            Map presc = restTemplate.getForObject(
                    backendUrl + "/prescriptions/search/findByIdPatient?patient=" + id,
                    Map.class);

            model.addAttribute("prescList",
                    presc != null ? ((Map) presc.get("_embedded")).get("prescriptions") : new ArrayList<>());
        } catch (Exception e) {
            model.addAttribute("prescList", new ArrayList<>());
        }

        return "patient/relations";
    }
}