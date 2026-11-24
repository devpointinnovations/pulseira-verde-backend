package br.com.saude_monitor.api.hospital.seed;

import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import lombok.Builder;

/** Documento montado aguardando gravação, com a origem para log. */
@Builder
record DocPendente(HospitalDocument doc, String origem) {
}
