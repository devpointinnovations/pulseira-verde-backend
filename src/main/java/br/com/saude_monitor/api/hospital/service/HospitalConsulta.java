package br.com.saude_monitor.api.hospital.service;

import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Consultas de hospital que outros módulos podem usar; o {@link HospitalRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class HospitalConsulta {

    private final HospitalRepository hospitalRepository;

    public boolean existsById(String id) {
        return hospitalRepository.existsById(id);
    }

    public Optional<HospitalDocument> findById(String id) {
        return hospitalRepository.findById(id);
    }

    public List<HospitalDocument> findAllById(Collection<String> ids) {
        return hospitalRepository.findAllById(ids);
    }
}
