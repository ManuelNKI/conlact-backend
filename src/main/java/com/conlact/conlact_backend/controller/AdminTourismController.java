package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.entity.TouristAttraction;
import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.repository.TouristAttractionRepository;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/turismo")
public class AdminTourismController {
    private final TouristAttractionRepository repository;
    public record Request(@NotBlank String nombre, String descripcion, String foto_url, String video_url,
        String duracion, String dificultad, Boolean is_published) {}
    @GetMapping public List<TouristAttractionResponse> list(){return repository.findAll().stream().map(TouristAttractionResponse::fromEntity).toList();}
    @PostMapping @Transactional public TouristAttractionResponse create(@Valid @RequestBody Request r){return save(new TouristAttraction(),r);}
    @PutMapping("/{id}") @Transactional public TouristAttractionResponse update(@PathVariable UUID id,@Valid @RequestBody Request r){return save(find(id),r);}
    @DeleteMapping("/{id}") @Transactional public void delete(@PathVariable UUID id){repository.delete(find(id));}
    private TouristAttraction find(UUID id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Ruta no encontrada"));}
    private TouristAttractionResponse save(TouristAttraction t,Request r){t.setName(r.nombre());t.setDescription(r.descripcion());t.setPhotoUrl(r.foto_url());t.setVideoUrl(r.video_url());t.setDuration(r.duracion());t.setDifficulty(r.dificultad());t.setIsPublished(r.is_published()!=Boolean.FALSE);t.setRequiresConfirmation(true);return TouristAttractionResponse.fromEntity(repository.save(t));}
}
