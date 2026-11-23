package br.com.saude_monitor.api.arquitetura;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * Fronteiras entre módulos de primeiro nível; ArchUnit + FreezingArchRule (e não Spring Modulith) porque não exige
 * reorganizar pacotes e as exceções atuais viram arquivo versionado em archunit_store/, que só deve encolher.
 */
@AnalyzeClasses(packages = "br.com.saude_monitor.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaModulosTest {

    private static final Pattern PACOTE = Pattern.compile("^br\\.com\\.saude_monitor\\.api\\.([^.]+)\\.([^.]+)");

    @ArchTest
    static final ArchRule modulosSemCiclos = freeze(modulosSemCiclos());

    @ArchTest
    static final ArchRule repositoryPrivadoDoModulo = freeze(acessoSoDoProprioModulo("repository"));

    @ArchTest
    static final ArchRule documentPrivadoDoModulo = freeze(acessoSoDoProprioModulo("document"));

    /**
     * Uma violação por aresta entre módulos que fecha um ciclo. Não usa slices().beFreeOfCycles() porque a descrição
     * dos ciclos varia a cada execução e quebraria o congelamento.
     */
    private static ArchRule modulosSemCiclos() {
        return classes()
                .should(new ArchCondition<JavaClass>("não formar ciclos entre módulos") {
                    private final Map<String, Set<String>> arestas = new HashMap<>();

                    @Override
                    public void init(Collection<JavaClass> classes) {
                        arestas.clear();
                        for (JavaClass origem : classes) {
                            String de = grupo(origem, 1);
                            for (Dependency d : origem.getDirectDependenciesFromSelf()) {
                                String para = grupo(d.getTargetClass(), 1);
                                if (!de.isEmpty() && !para.isEmpty() && !de.equals(para)) {
                                    arestas.computeIfAbsent(de, k -> new TreeSet<>()).add(para);
                                }
                            }
                        }
                    }

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        // avaliado em finish(), com o grafo completo
                    }

                    @Override
                    public void finish(ConditionEvents events) {
                        arestas.forEach((de, alvos) -> alvos.stream()
                                .filter(para -> alcanca(para, de, new TreeSet<>()))
                                .forEach(para -> events.add(SimpleConditionEvent.violated(
                                        de + "->" + para, "Ciclo entre módulos: " + de + " -> " + para))));
                    }

                    private boolean alcanca(String origem, String destino, Set<String> visitados) {
                        if (origem.equals(destino)) {
                            return true;
                        }
                        return visitados.add(origem) && arestas.getOrDefault(origem, Set.of()).stream()
                                .anyMatch(prox -> alcanca(prox, destino, visitados));
                    }
                })
                .as("Módulos de primeiro nível não podem formar ciclos");
    }

    private static ArchRule acessoSoDoProprioModulo(String camada) {
        return classes()
                .should(new ArchCondition<JavaClass>("acessar " + camada + " apenas do próprio módulo") {
                    @Override
                    public void check(JavaClass origem, ConditionEvents events) {
                        String moduloOrigem = grupo(origem, 1);
                        for (Dependency d : origem.getDirectDependenciesFromSelf()) {
                            JavaClass alvo = d.getTargetClass();
                            String moduloAlvo = grupo(alvo, 1);
                            if (camada.equals(grupo(alvo, 2)) && !moduloAlvo.equals(moduloOrigem)) {
                                events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                            }
                        }
                    }
                })
                .as("Classes de " + camada + " de um módulo só podem ser acessadas pelo próprio módulo");
    }

    private static String grupo(JavaClass c, int n) {
        Matcher m = PACOTE.matcher(c.getName());
        return m.find() ? m.group(n) : "";
    }
}
