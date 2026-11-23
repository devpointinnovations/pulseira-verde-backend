# Monitoramento: latência e erro por rota (E8-06)

Regras de alerta e painel sobre as métricas que o backend já expõe em `/actuator/prometheus`
(histograma do `http.server.requests`, por método, rota e status; ver `application.properties`).

São **portáteis**: só PromQL sobre as séries do próprio backend, sem nada da nuvem. Valem para
qualquer Prometheus, ou serviço compatível, que colete o backend.

| Arquivo | O que é |
|---|---|
| `prometheus/regras-latencia.yml` | Regras de gravação (`pv:*`: p50, p95, p99, requisições/s e 5xx por rota) e os alertas |
| `prometheus/regras-latencia.test.yml` | Testes das regras (`promtool test rules`), rodados no CI |
| `grafana/painel-operacao.json` | Painel "Operação": resumo por rota e séries de p50, p95, p99, requisições/s e 5xx |

## Alertas

| Alerta | Dispara quando | Severidade |
|---|---|---|
| `LatenciaP95AcimaDoOrcamento` | p95 de uma rota acima de **300 ms** por 10 min, com pelo menos 3 requisições/min nela. O login fica fora | aviso |
| `LatenciaP95LoginAlta` | p95 do login (`/api/v1/auth/login` ou `/api/v1/auth/painel/login`) acima de **500 ms** por 10 min, com o mesmo piso de tráfego | aviso |
| `TaxaDe5xxAcimaDe1PorCento` | Mais de **1%** das respostas do serviço com 5xx por 10 min, com o mesmo piso | crítico |
| `BackendSemColeta` | O Prometheus não consegue coletar o backend por 5 min | crítico |

**Os limiares são provisórios.** 300 ms é o RNF-02. 1% e 10 min vêm da análise de pendências de
13/11/2026. O login ganhou limiar próprio porque mediu 310 ms de p95 em 23/09/2026 (BCrypt numa
vCPU), e o relatório de desempenho recomendou aceitar e documentar. A calibragem definitiva vem
da prova de carga no ambiente novo (P13/PERF-01). Mudou um limiar? Ajuste o teste junto.

**Piso de tráfego.** Com pouca requisição, uma só lenta (cold start, primeira consulta depois de
ocioso) vira o p95 inteiro da janela. Por isso os alertas de latência e de 5xx só valem com
pelo menos 0,05 requisição/s (3 por minuto) na janela de 5 min.

## Como ler as séries

- **`/actuator/*` fica de fora** de todas as regras: é a própria coleta e a sonda de saúde.
- **`uri="UNKNOWN"`** é resposta dada antes de chegar a um controller: 429 do limite de
  requisições ou 401 da segurança (rota inexistente sem token também cai no 401). Visto na
  validação local de 23/11/2026: 429 do limitador e 401 sem token, todos com `UNKNOWN`.
- **Job de coleta com nome começando por `backend`** (ex.: `backend-hom`). As regras e o painel
  filtram por essa convenção para não misturar outras coletas.
- O painel só lê as séries `pv:*`: sem as regras carregadas no Prometheus, ele fica vazio.

## Onde já roda

- **CI** (`ci.yml`, job "Regras e painel de monitoramento"): `promtool check rules`,
  `promtool test rules` e a validação do JSON do painel.
- **Teste de desempenho** (`perf/`): o Prometheus local carrega as regras e o Grafana local mostra o
  painel "Operação" ao lado do de desempenho. Lá não há Alertmanager: os alertas aparecem em
  `http://localhost:9090/alerts`, mas não notificam ninguém.

## O que ainda falta (depende da migração de infraestrutura)

A infraestrutura vai sair do Cloud Run. Estes três pontos dependem do destino e ficam para o
trabalho da migração:

1. **Onde o coletor roda e guarda os dados**: na mesma máquina do backend, numa separada ou num
   serviço gerenciado compatível com Prometheus.
2. **Coleta sem o token ADMIN**: hoje `/actuator/prometheus` exige papel ADMIN, e o `perf/` contorna
   isso renovando um token a cada 10 min. No ambiente novo, a proposta é uma porta de
   gerenciamento só na rede interna (`management.server.port`), com revisão de segurança.
3. **Notificação no celular**: Alertmanager, ou o equivalente do destino, com um canal que chegue
   ao celular do PO, e um alerta de teste provado.

Até lá o E8-06 fica parcial.
