package br.com.saude_monitor.api.regiao.service;

import lombok.Builder;
import org.locationtech.jts.geom.Geometry;

@Builder
record RegiaoPoligono(String nome, Geometry geometria) {
}
