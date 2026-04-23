package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.AchatAnnonceRequest;
import com.fursa.fursa_backend.dto.AchatAnnonceResponse;
import com.fursa.fursa_backend.dto.AnnonceRequest;
import com.fursa.fursa_backend.dto.AnnonceResponse;
import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Transaction;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.StatutTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.PaiementRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnnonceService {

    private final AnnonceRepository annonceRepository;
    private final InvestisseurRepository investisseurRepository;
    private final ProprieteRepository proprieteRepository;
    private final PossessionRepository possessionRepository;
    private final PaiementRepository paiementRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    @Transactional
    public AnnonceResponse creer(Long vendeurId, AnnonceRequest request) {
        Investisseur vendeur = investisseurRepository.findById(vendeurId)
                .orElseThrow(() -> new EntityNotFoundException("Investisseur non trouve: id=" + vendeurId));
        Propriete propriete = proprieteRepository.findById(request.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + request.proprieteId()));

        Possession possession = possessionRepository
                .findByInvestisseurIdAndProprieteId(vendeur.getId(), propriete.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Le vendeur ne possede aucune part de la propriete " + propriete.getId()));

        int partsDejaEnVente = annonceRepository
                .findByProprieteIdAndStatut(propriete.getId(), StatutAnnonce.OUVERTE).stream()
                .filter(a -> a.getInvestisseur().getId().equals(vendeur.getId()))
                .mapToInt(Annonce::getNombreDePartsAVendre)
                .sum();

        int disponibles = possession.getNombreDeParts() - partsDejaEnVente;
        if (request.nombreDePartsAVendre() > disponibles) {
            throw new IllegalStateException("Parts insuffisantes : dispo=" + disponibles
                    + ", demande=" + request.nombreDePartsAVendre());
        }

        Annonce annonce = new Annonce();
        annonce.setInvestisseur(vendeur);
        annonce.setPropriete(propriete);
        annonce.setNombreDePartsAVendre(request.nombreDePartsAVendre());
        annonce.setPrixUnitaireDemande(request.prixUnitaireDemande());
        annonce.setStatut(StatutAnnonce.OUVERTE);

        return toResponse(annonceRepository.save(annonce));
    }

    public List<AnnonceResponse> listerOuvertes() {
        return annonceRepository.findByStatut(StatutAnnonce.OUVERTE).stream().map(this::toResponse).toList();
    }

    public List<AnnonceResponse> listerParVendeur(Long vendeurId) {
        return annonceRepository.findByInvestisseurId(vendeurId).stream().map(this::toResponse).toList();
    }

    public AnnonceResponse getById(Long id) {
        return toResponse(findOuThrow(id));
    }

    @Transactional
    public AnnonceResponse annuler(Long annonceId, Long vendeurId) {
        Annonce annonce = findOuThrow(annonceId);
        if (!annonce.getInvestisseur().getId().equals(vendeurId)) {
            throw new IllegalStateException("Seul le vendeur peut annuler son annonce");
        }
        if (annonce.getStatut() != StatutAnnonce.OUVERTE) {
            throw new IllegalStateException("Annonce non modifiable (statut=" + annonce.getStatut() + ")");
        }
        annonce.setStatut(StatutAnnonce.ANNULEE);
        return toResponse(annonceRepository.save(annonce));
    }

    @Transactional
    public AchatAnnonceResponse acheter(Long annonceId, Long acheteurId, AchatAnnonceRequest request) {
        Annonce annonce = findOuThrow(annonceId);

        if (annonce.getStatut() != StatutAnnonce.OUVERTE) {
            throw new IllegalStateException("Annonce non disponible a l'achat (statut=" + annonce.getStatut() + ")");
        }

        Investisseur vendeur = annonce.getInvestisseur();
        Investisseur acheteur = investisseurRepository.findById(acheteurId)
                .orElseThrow(() -> new EntityNotFoundException("Acheteur non trouve: id=" + acheteurId));

        if (vendeur.getId().equals(acheteur.getId())) {
            throw new IllegalStateException("Un investisseur ne peut pas acheter sa propre annonce");
        }

        int nbDemande = request.nombreDeParts();
        if (nbDemande > annonce.getNombreDePartsAVendre()) {
            throw new IllegalStateException("Parts demandees > parts disponibles sur l'annonce ("
                    + nbDemande + " > " + annonce.getNombreDePartsAVendre() + ")");
        }

        Propriete propriete = annonce.getPropriete();

        Possession possessionVendeur = possessionRepository
                .findByInvestisseurIdAndProprieteId(vendeur.getId(), propriete.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Le vendeur n'a plus de possession sur cette propriete"));

        if (possessionVendeur.getNombreDeParts() < nbDemande) {
            throw new IllegalStateException("Possession du vendeur insuffisante ("
                    + possessionVendeur.getNombreDeParts() + " < " + nbDemande + ")");
        }

        possessionVendeur.setNombreDeParts(possessionVendeur.getNombreDeParts() - nbDemande);
        if (possessionVendeur.getNombreDeParts() == 0) {
            possessionRepository.delete(possessionVendeur);
        } else {
            possessionRepository.save(possessionVendeur);
        }

        Possession possessionAcheteur = possessionRepository
                .findByInvestisseurIdAndProprieteId(acheteur.getId(), propriete.getId())
                .orElseGet(() -> {
                    Possession p = new Possession();
                    p.setInvestisseur(acheteur);
                    p.setPropriete(propriete);
                    p.setNombreDeParts(0);
                    return p;
                });
        possessionAcheteur.setNombreDeParts(possessionAcheteur.getNombreDeParts() + nbDemande);
        possessionRepository.save(possessionAcheteur);

        BigDecimal montantTotal = annonce.getPrixUnitaireDemande().multiply(BigDecimal.valueOf(nbDemande));

        Paiement paiement = new Paiement();
        paiement.setInvestisseur(acheteur);
        paiement.setPropriete(propriete);
        paiement.setMontant(montantTotal);
        paiement.setType(TypePaiement.FIAT);
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setDate(LocalDateTime.now());
        paiement.setNombre_parts(nbDemande);
        paiement = paiementRepository.save(paiement);

        Transaction transaction = new Transaction();
        transaction.setPaiement(paiement);
        transaction.setTypeOperation("VENTE_SECONDAIRE");
        transaction.setNombreParts(nbDemande);
        transaction.setMontant(montantTotal);
        transaction.setDateTransaction(LocalDateTime.now());
        transaction.setStatut(StatutTransaction.SUCCES);
        transaction.setHashTransaction(UUID.randomUUID().toString());
        transaction = transactionRepository.save(transaction);

        annonce.setNombreDePartsAVendre(annonce.getNombreDePartsAVendre() - nbDemande);
        if (annonce.getNombreDePartsAVendre() == 0) {
            annonce.setStatut(StatutAnnonce.COMPLETEE);
        }
        annonceRepository.save(annonce);

        notificationService.envoyer(vendeur,
                "Vente realisee",
                nbDemande + " part(s) de " + propriete.getNom() + " vendue(s) pour " + montantTotal + " EUR",
                TypeMessage.TRANSACTION);
        notificationService.envoyer(acheteur,
                "Achat realise",
                nbDemande + " part(s) de " + propriete.getNom() + " achetee(s) pour " + montantTotal + " EUR",
                TypeMessage.TRANSACTION);

        return new AchatAnnonceResponse(
                annonce.getId(),
                transaction.getId(),
                paiement.getId(),
                acheteur.getId(),
                vendeur.getId(),
                propriete.getId(),
                nbDemande,
                montantTotal,
                transaction.getHashTransaction(),
                annonce.getStatut().name()
        );
    }

    private Annonce findOuThrow(Long id) {
        return annonceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Annonce non trouvee: id=" + id));
    }

    private AnnonceResponse toResponse(Annonce a) {
        Investisseur v = a.getInvestisseur();
        Propriete p = a.getPropriete();
        return new AnnonceResponse(
                a.getId(),
                v == null ? null : v.getId(),
                v == null ? null : v.getPrenom() + " " + v.getNom(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                a.getNombreDePartsAVendre(),
                a.getPrixUnitaireDemande(),
                a.getStatut()
        );
    }
}
