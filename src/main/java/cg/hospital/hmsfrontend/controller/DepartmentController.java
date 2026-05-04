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
@RequestMapping("/department")
public class DepartmentController {

    @Autowired private RestTemplate restTemplate;
    @Value("${backend.base.url}") private String backendUrl;

    // ===================== ENRICH =====================
    private void enrichDepartments(List<Map> list) {
        for (Map d : list) {
            try {
                // 🔹 Extract departmentId
                if (d.get("departmentId") == null && d.get("departmentID") == null) {
                    String href = (String) ((Map)((Map) d.get("_links")).get("self")).get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();
                    int id = Integer.parseInt(href.substring(href.lastIndexOf("/") + 1));
                    d.put("departmentId", id);
                }

                // 🔹 HEAD (call API instead of parsing)
                Map links = (Map) d.get("_links");

                if (links != null && links.get("head") != null) {
                    String href = (String) ((Map) links.get("head")).get("href");

                    Map phy = restTemplate.getForObject(href, Map.class);

                    if (phy != null) {
                        d.put("headName", phy.get("name"));

                        String phyHref = (String) ((Map)((Map) phy.get("_links")).get("self")).get("href");
                        int pid = Integer.parseInt(phyHref.substring(phyHref.lastIndexOf("/") + 1));

                        d.put("headId", pid);
                    }
                }

            } catch (Exception ignored) {}
        }
    }

    // ===================== LIST =====================
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String name,
                       Model model) {

        try {
            List<Map> list;
            int totalPages = 1;

            if (name != null && !name.isBlank()) {
                try {
                    ResponseEntity<Map> res = restTemplate.getForEntity(
                            backendUrl + "/departments/search/findByName?name=" + name, Map.class);

                    list = new ArrayList<>();
                    if (res.getBody() != null) list.add(res.getBody());

                } catch (Exception ex) {
                    list = new ArrayList<>();
                }

                model.addAttribute("searchName", name);

            } else {
                ResponseEntity<Map> res = restTemplate.getForEntity(
                        backendUrl + "/departments?page=" + page + "&size=" + size, Map.class);

                Map body = res.getBody();
                Map emb = body != null ? (Map) body.get("_embedded") : null;
                list = emb != null ? (List<Map>) emb.get("departments") : new ArrayList<>();

                Map pageInfo = body != null ? (Map) body.get("page") : null;
                totalPages = pageInfo != null ? ((Number) pageInfo.get("totalPages")).intValue() : 1;
            }

            enrichDepartments(list);

            model.addAttribute("items", list);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("size", size);

        } catch (Exception e) {
            model.addAttribute("items", new ArrayList<>());
            model.addAttribute("error", "Backend Error: " + e.getMessage());
        }

        return "department/list";
    }

    // ===================== SAVE =====================
    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("departmentID", Integer.parseInt(params.get("departmentID")));
            body.put("name", params.get("name"));
            body.put("head", backendUrl + "/physicians/" + params.get("headId"));

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            String deptId = params.get("departmentID");

            try {
                restTemplate.getForEntity(backendUrl + "/departments/" + deptId, Map.class);
                restTemplate.exchange(backendUrl + "/departments/" + deptId, HttpMethod.PUT, req, Map.class);
            } catch (Exception e) {
                restTemplate.exchange(backendUrl + "/departments", HttpMethod.POST, req, Map.class);
            }

        } catch (Exception ignored) {}

        return "redirect:/department/list";
    }

    // ===================== RELATIONS =====================
    @GetMapping("/relations")
    public String relations(@RequestParam(defaultValue = "1") int id, Model model) {

        try {
            // 🔹 All departments
            ResponseEntity<Map> allRes = restTemplate.getForEntity(
                    backendUrl + "/departments?size=50", Map.class);

            Map allEmb = allRes.getBody() != null ? (Map) allRes.getBody().get("_embedded") : null;
            List<Map> allDepts = allEmb != null ? (List<Map>) allEmb.get("departments") : new ArrayList<>();

            enrichDepartments(allDepts);

            model.addAttribute("allItems", allDepts);
            model.addAttribute("selectedId", id);

            // 🔹 Selected department
            Map dept = restTemplate.getForObject(
                    backendUrl + "/departments/" + id, Map.class);

            List<Map> single = new ArrayList<>();
            if (dept != null) single.add(dept);

            enrichDepartments(single);

            model.addAttribute("selectedItem", single.isEmpty() ? null : single.get(0));

            // 🔹 Affiliated physicians
            ResponseEntity<Map> afRes = restTemplate.getForEntity(
                    backendUrl + "/affiliated-with?size=100", Map.class);

            Map afEmb = afRes.getBody() != null ? (Map) afRes.getBody().get("_embedded") : null;
            List<Map> allAff = afEmb != null ? (List<Map>) afEmb.get("affiliatedWith") : new ArrayList<>();

            List<Map> affList = new ArrayList<>();

            for (Map a : allAff) {
                try {
                    String href = (String) ((Map)((Map) a.get("_links")).get("self")).get("href");
                    href = href.replaceAll("\\{.*\\}", "").trim();

                    String key = href.substring(href.lastIndexOf("/") + 1);
                    String[] parts = key.split("_");

                    int deptId = Integer.parseInt(parts[1]);

                    if (deptId == id) {

                        String phyHref = (String) ((Map)((Map) a.get("_links")).get("physician")).get("href");

                        Map phy = restTemplate.getForObject(phyHref, Map.class);

                        if (phy != null) {
                            String self = (String) ((Map)((Map) phy.get("_links")).get("self")).get("href");
                            int pid = Integer.parseInt(self.substring(self.lastIndexOf("/") + 1));

                            a.put("physicianId", pid);
                            a.put("physicianName", phy.get("name"));
                            a.put("position", phy.get("position"));
                        }

                        affList.add(a);
                    }

                } catch (Exception ignored) {}
            }

            model.addAttribute("affiliatedList", affList);

            // 🔹 PRIMARY LIST FIX
            List<Map> primaryList = new ArrayList<>();

            for (Map a : affList) {
                try {
                    Object val = a.get("primaryAffiliation");

                    boolean isPrimary = false;

                    if (val instanceof Boolean) {
                        isPrimary = (Boolean) val;
                    } else if (val instanceof Number) {
                        isPrimary = ((Number) val).intValue() == 1;
                    } else if (val instanceof String) {
                        isPrimary = val.equals("0x01") || val.equals("1");
                    }

                    if (isPrimary) {
                        primaryList.add(a);
                    }

                } catch (Exception ignored) {}
            }

            model.addAttribute("primaryList", primaryList);

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "department/relations";
    }
}