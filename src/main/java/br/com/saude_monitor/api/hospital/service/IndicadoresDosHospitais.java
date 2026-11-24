package br.com.saude_monitor.api.hospital.service;

import br.com.saude_monitor.api.hospital.dto.IndicadoresResponse;

import java.util.Collection;
import java.util.List;

/**
 * De onde o hospital lê os indicadores que mostra no catálogo e no ranking. O módulo {@code indicador} a
 * implementa; assim o hospital não depende do indicador.
 */
public interface IndicadoresDosHospitais {

    /**
     * Indicadores embutidos dos hospitais informados, na mesma ordem dos ids; o hospital sem indicador
     * materializado vem com os campos de valor nulos.
     */
    List<IndicadoresResponse> mapaIndicadores(Collection<String> hospitalIds);
}
