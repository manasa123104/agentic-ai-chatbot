package com.agentic.chatbot.service;

import com.agentic.chatbot.model.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectModuleMapperTest {

    private final CodeSampleLibrary library = new CodeSampleLibrary();
    private final ProgrammingLanguage java = library.language("java").orElseThrow();

    private List<ProjectModuleMapper.SourceFile> project() {
        return List.of(
                new ProjectModuleMapper.SourceFile("src/main/java/com/acme/app/Main.java",
                        "package com.acme.app;\nimport com.acme.order.OrderService;\npublic class Main {\n"
                                + "  public static void main(String[] a) { new OrderService().total(); }\n}\n", java),
                new ProjectModuleMapper.SourceFile("src/main/java/com/acme/order/OrderService.java",
                        "package com.acme.order;\npublic class OrderService {\n  private final OrderRepository repo = new OrderRepository();\n"
                                + "  public double total() { return repo.all().size() * 1.5; }\n}\n", java),
                new ProjectModuleMapper.SourceFile("src/main/java/com/acme/order/OrderRepository.java",
                        "package com.acme.order;\nimport java.util.List;\npublic class OrderRepository {\n"
                                + "  public List<String> all() { return List.of(\"a\"); }\n}\n", java));
    }

    private ProjectModuleMapper mapper(String target) {
        return new ProjectModuleMapper(library.language(target).orElseThrow());
    }

    @Test
    void ordersFilesDependenciesFirst() {
        List<ProjectModuleMapper.Module> plan = mapper("python").plan(project());
        List<String> order = plan.stream().map(m -> ProjectModuleMapper.baseName(m.source().path())).toList();
        assertEquals(List.of("OrderRepository", "OrderService", "Main"), order);
        assertEquals(List.of("src/main/java/com/acme/order/OrderService.java"), plan.get(2).dependencies());
    }

    @Test
    void mapsPathsAndImportsForPython() {
        ProjectModuleMapper mapper = mapper("python");
        List<ProjectModuleMapper.Module> plan = mapper.plan(project());
        Map<String, ProjectModuleMapper.Module> bySource = new HashMap<>();
        plan.forEach(m -> bySource.put(m.source().path(), m));

        ProjectModuleMapper.Module service = plan.get(1);
        assertEquals("com/acme/order/order_service.py", service.targetPath());

        String context = mapper.context(plan.get(2), bySource,
                Map.of(service.source().path(), "class OrderService:\n    def total(self) -> float:\n        return 1.5\n"));
        assertTrue(context.contains("from com.acme.order.order_service import OrderService"), context);
        assertTrue(context.contains("def total(self) -> float:"), context);

        Map<String, String> packages = mapper.packageFiles(plan);
        assertTrue(packages.containsKey("com/acme/order/__init__.py"));
        assertTrue(packages.containsKey("com/__init__.py"));
    }

    @Test
    void mapsPathsAndImportsForOtherTargets() {
        List<ProjectModuleMapper.SourceFile> files = project();

        ProjectModuleMapper ts = mapper("typescript");
        List<ProjectModuleMapper.Module> tsPlan = ts.plan(files);
        assertEquals("src/com/acme/order/OrderService.ts", tsPlan.get(1).targetPath());
        assertEquals("import { OrderService } from '../order/OrderService';", ts.importStatement(tsPlan.get(1), tsPlan.get(2)));

        ProjectModuleMapper cs = mapper("csharp");
        List<ProjectModuleMapper.Module> csPlan = cs.plan(files);
        assertEquals("Com/Acme/Order/OrderService.cs", csPlan.get(1).targetPath());
        assertEquals("using Com.Acme.Order;", cs.importStatement(csPlan.get(1), csPlan.get(2)));

        ProjectModuleMapper go = mapper("go");
        List<ProjectModuleMapper.Module> goPlan = go.plan(files);
        assertEquals("com/acme/order/order_service.go", goPlan.get(1).targetPath());
        assertEquals("import \"converted/com/acme/order\"", go.importStatement(goPlan.get(1), goPlan.get(2)));
        assertTrue(go.packageFiles(goPlan).containsKey("go.mod"));

        ProjectModuleMapper rust = mapper("rust");
        List<ProjectModuleMapper.Module> rustPlan = rust.plan(files);
        assertEquals("src/main.rs", rustPlan.get(2).targetPath());
        assertEquals("use converted::com::acme::order::order_service::OrderService;",
                rust.importStatement(rustPlan.get(1), rustPlan.get(2)));
        assertEquals("use crate::com::acme::order::order_repository::OrderRepository;",
                rust.importStatement(rustPlan.get(0), rustPlan.get(1)));
        assertTrue(rust.packageFiles(rustPlan).get("src/lib.rs").contains("pub mod com;"));
    }
}
