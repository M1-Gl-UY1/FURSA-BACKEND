package com.fursa.fursa_backend.feature.command;

import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class CancelAnnonceCommand implements Command {

    private final BlockchainService blockChainService;
    private final AnnonceRepository annonceRepository;
    private final Long annonceId;

    private Annonce annonce;
    private StatutAnnonce statutPrecedent;

    @Override
    @Transactional
    public void execute() {
        annonce = annonceRepository.findById(annonceId)
                .orElseThrow(() -> new RuntimeException("Annonce non trouvée"));

        statutPrecedent = annonce.getStatut();
        annonce.setStatut(StatutAnnonce.ANNULEE);
        annonceRepository.save(annonce);

        blockChainService.unlockShares(String.valueOf(annonceId));
    }

    @Override
    @Transactional
    public void undo() {
        if (annonce != null && statutPrecedent != null) {
            annonce.setStatut(statutPrecedent);
            annonceRepository.save(annonce);
            blockChainService.relockShares(String.valueOf(annonceId));
        }
    }

    @Override
    public String getTransactionHash() {
        return null;
    }
}