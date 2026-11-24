package br.com.saude_monitor.api.feedback.seed;

/**
 * Perfil de qualidade de um hospital. {@code pesosNota[i]} é o peso da nota {@code i+1};
 * o tempo de permanência de cada visita sai de [{@code minutosMin}, {@code minutosMax}].
 */
public enum PerfilDoHospital {
    EXCELENTE(new int[]{1, 1, 4, 24, 70}, 25, 70, 10, 30),
    BOM(new int[]{3, 7, 20, 45, 25}, 50, 130, 8, 35),
    REGULAR(new int[]{10, 20, 40, 20, 10}, 100, 260, 6, 25),
    RUIM(new int[]{40, 30, 18, 8, 4}, 200, 600, 6, 20),
    POUCAS_AVALIACOES(new int[]{10, 15, 25, 30, 20}, 40, 240, 1, 4);

    final int[] pesosNota;
    final int minutosMin;
    final int minutosMax;
    final int avaliacoesMin;
    final int avaliacoesMax;

    PerfilDoHospital(int[] pesosNota, int minutosMin, int minutosMax, int avaliacoesMin, int avaliacoesMax) {
        this.pesosNota = pesosNota;
        this.minutosMin = minutosMin;
        this.minutosMax = minutosMax;
        this.avaliacoesMin = avaliacoesMin;
        this.avaliacoesMax = avaliacoesMax;
    }
}
