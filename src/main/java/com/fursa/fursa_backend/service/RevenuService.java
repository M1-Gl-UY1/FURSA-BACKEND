package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.RevenuRequest;
import com.fursa.fursa_backend.dto.RevenuResponse;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenuService {

    private final RevenusRepository revenusRepository;
    private final ProprieteRepository proprieteRepository;

    @Transactional
    public RevenuResponse creer(RevenuRequest request) {
        Propriete propriete = proprieteRepository.findById(request.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + request.proprieteId()));

        Revenus revenu = new Revenus();
        revenu.setPropriete(propriete);
        revenu.setMontantTotal(request.montantTotal());
        revenu.setDate(request.date() != null ? request.date() : LocalDate.now());
        return toResponse(revenusRepository.save(revenu));
    }

    public List<RevenuResponse> lister() {
        return revenusRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<RevenuResponse> listerParPropriete(Long proprieteId) {
        return revenusRepository.findByProprieteId(proprieteId).stream().map(this::toResponse).toList();
    }

    public RevenuResponse getById(Long id) {
        return toResponse(revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id)));
    }

    private RevenuResponse toResponse(Revenus r) {
        Propriete p = r.getPropriete();
        return new RevenuResponse(
                r.getId(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                r.getDate(),
                r.getMontantTotal()
        );
    }
}
