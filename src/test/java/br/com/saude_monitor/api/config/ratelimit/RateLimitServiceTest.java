package br.com.saude_monitor.api.config.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do {@link RateLimitService} (F0-04): janela deslizante e limpeza.
 */
class RateLimitServiceTest {

    @Test
    void devePermitirAteOLimiteDoGrupo() {
        RateLimitService service = new RateLimitService(RateLimitFilterTest.PADRAO);
        for (int i = 0; i < 10; i++) {
            assertThat(service.tentarConsumir("ip", GrupoDeLimite.AUTH)).isTrue();
        }
        assertThat(service.tentarConsumir("ip", GrupoDeLimite.AUTH)).isFalse();
    }

    @Test
    void deveManterContadoresIndependentesPorChave() {
        RateLimitService service = new RateLimitService(RateLimitFilterTest.PADRAO);
        for (int i = 0; i < 60; i++) {
            service.tentarConsumir("ipA", GrupoDeLimite.PUBLICO);
        }
        assertThat(service.tentarConsumir("ipA", GrupoDeLimite.PUBLICO)).isFalse();
        assertThat(service.tentarConsumir("ipB", GrupoDeLimite.PUBLICO)).isTrue();
    }

    @Test
    void limiteDeCadaGrupoVemDaConfiguracao() {
        RateLimitService service = new RateLimitService(new RateLimitProperties(3, 5, 1, 600));
        assertThat(service.limite(GrupoDeLimite.AUTH)).isEqualTo(3);
        assertThat(service.limite(GrupoDeLimite.PUBLICO)).isEqualTo(5);
        for (int i = 0; i < 3; i++) {
            assertThat(service.tentarConsumir("ip", GrupoDeLimite.AUTH)).isTrue();
        }
        assertThat(service.tentarConsumir("ip", GrupoDeLimite.AUTH)).isFalse();
    }

    @Test
    void registrarDevolveQuantasAChaveJaFezNaJanela() {
        RateLimitService service = new RateLimitService(RateLimitFilterTest.PADRAO);
        assertThat(service.registrar("ip", GrupoDeLimite.AUTH)).isEqualTo(1);
        assertThat(service.registrar("ip", GrupoDeLimite.AUTH)).isEqualTo(2);
        assertThat(service.registrar("outro", GrupoDeLimite.AUTH)).isEqualTo(1);
    }

    @Test
    void deveLimparJanelasObsoletas() {
        RateLimitService service = new RateLimitService(RateLimitFilterTest.PADRAO);
        service.tentarConsumir("ip-antigo", GrupoDeLimite.PUBLICO);
        // Força o estado com janela antiga para validar a limpeza.
        long agora = System.currentTimeMillis();
        service.limparObsoletos(agora + (5 * RateLimitService.JANELA_MS));
        // Após limpar, a mesma chave pode consumir novamente.
        assertThat(service.tentarConsumir("ip-antigo", GrupoDeLimite.PUBLICO)).isTrue();
    }

    @Test
    void contadorRecomecaQuandoAJanelaDeUmMinutoVira() {
        AtomicLong agoraEmMs = new AtomicLong(RateLimitService.JANELA_MS - 1);
        RateLimitService service = new RateLimitService(RateLimitFilterTest.PADRAO, agoraEmMs::get);
        for (int i = 0; i < 10; i++) {
            service.tentarConsumir("ip", GrupoDeLimite.AUTH);
        }
        assertThat(service.tentarConsumir("ip", GrupoDeLimite.AUTH)).isFalse();

        agoraEmMs.incrementAndGet();

        assertThat(service.tentarConsumir("ip", GrupoDeLimite.AUTH)).isTrue();
    }

    @Test
    void loginERefreshUsamOLimiteDeAutenticacao() {
        RateLimitService service = new RateLimitService(RateLimitFilterTest.PADRAO);
        assertThat(service.limite(GrupoDeLimite.LOGIN)).isEqualTo(10);
        assertThat(service.limite(GrupoDeLimite.REFRESH)).isEqualTo(10);
    }

    @Test
    void tetoPorIpNuncaFicaAbaixoDosOutrosLimites() {
        assertThat(new RateLimitService(RateLimitFilterTest.PADRAO).limite(GrupoDeLimite.IP))
                .isEqualTo(600);
        // Homologação: público 2000/min para o teste de carga, que sai de um IP só.
        assertThat(new RateLimitService(new RateLimitProperties(300, 2000, 1, 600)).limite(GrupoDeLimite.IP))
                .isEqualTo(2000);
        assertThat(new RateLimitService(new RateLimitProperties(700, 60, 1, 600)).limite(GrupoDeLimite.IP))
                .isEqualTo(700);
    }
}
