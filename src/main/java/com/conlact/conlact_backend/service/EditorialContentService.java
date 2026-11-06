package com.conlact.conlact_backend.service;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.repository.*;
import com.conlact.conlact_backend.dto.association.AssociationUpdateRequest;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class EditorialContentService {
    private final TouristAttractionRepository tourism;
    private final TestimonialRepository testimonials;
    private final AssociationRepository associations;
    private final AssociationService associationService;
    private final Validator validator;
    @SuppressWarnings("unchecked")
    public Map<String,Object> sync(String slug,Map<String,Object> content,Map<String,Object> previous) {
        Map<String,Object> result=new LinkedHashMap<>(content);
        if(slug.equals("turismo") && content.get("atractivos") instanceof List<?> list){
            List<Map<String,Object>> saved=new ArrayList<>();Set<UUID> kept=new HashSet<>();
            for(Object raw:list){Map<String,Object> row=new LinkedHashMap<>((Map<String,Object>)raw);UUID id=uuid(row.get("id"));
                TouristAttraction t=id==null?new TouristAttraction():tourism.findById(id).orElseThrow(()->new com.conlact.conlact_backend.exception.ResourceNotFoundException("Contenido no encontrado"));
                t.setName(required(row,"nombre"));t.setDescription(text(row,"descripcion"));t.setPhotoUrl(text(row,"foto_url"));t.setVideoUrl(text(row,"video_url"));t.setDuration(text(row,"duracion"));t.setDifficulty(text(row,"dificultad"));t.setIsPublished(true);t.setRequiresConfirmation(true);
                t=tourism.saveAndFlush(t);kept.add(t.getId());row.put("id",t.getId().toString());saved.add(row);
            }
            for(UUID id:previousIds(previous,"atractivos")) if(!kept.contains(id)) tourism.findById(id).ifPresent(t->{t.setIsPublished(false);tourism.save(t);});
            result.put("atractivos",saved);
        }
        if(slug.equals("nosotros") && content.get("testimonials") instanceof List<?> list){
            List<Map<String,Object>> saved=new ArrayList<>();Set<UUID> kept=new HashSet<>();
            for(Object raw:list){Map<String,Object> row=new LinkedHashMap<>((Map<String,Object>)raw);UUID id=uuid(row.get("id"));
                Testimonial t=id==null?new Testimonial():testimonials.findById(id).orElseThrow(()->new com.conlact.conlact_backend.exception.ResourceNotFoundException("Contenido no encontrado"));
                t.setAuthorName(required(row,"name"));t.setAuthorType(text(row,"role"));t.setQuote(required(row,"quote"));
                if(t.getId()==null){t.setIsAuthorized(false);t.setIsApproved(false);t.setIsPublished(false);t.setIsArchived(false);t.setIsFeatured(false);}
                if(row.get("authorized") instanceof Boolean authorized){t.setIsAuthorized(authorized);t.setIsApproved(authorized);t.setIsPublished(authorized);}
                t=testimonials.saveAndFlush(t);kept.add(t.getId());row.put("id",t.getId().toString());saved.add(row);
            }
            for(UUID id:previousIds(previous,"testimonials")) if(!kept.contains(id)) testimonials.findById(id).ifPresent(t->{t.setIsPublished(false);testimonials.save(t);});
            result.put("testimonials",saved);
        }
        if(slug.equals("asociaciones") && content.get("asociaciones") instanceof List<?> list){
            List<Map<String,Object>> saved=new ArrayList<>();Set<UUID> kept=new HashSet<>();
            for(Object raw:list){Map<String,Object> row=new LinkedHashMap<>((Map<String,Object>)raw);UUID id=uuid(row.get("id"));
                Association a=id==null?new Association():associations.findById(id).orElseThrow(()->new com.conlact.conlact_backend.exception.ResourceNotFoundException("Contenido no encontrado"));
                a.setName(required(row,"nombre"));a.setSlug(required(row,"slug"));a.setShortDescription(text(row,"descripcion"));a.setLocationText(text(row,"parroquia"));a.setIsPublished(true);
                a=associations.saveAndFlush(a);kept.add(a.getId());row.put("id",a.getId().toString());saved.add(row);
            }
            for(UUID id:previousIds(previous,"asociaciones"))if(!kept.contains(id))associations.findById(id).ifPresent(a->{a.setIsPublished(false);associations.save(a);});
            result.put("asociaciones",saved);
        }
        if(slug.startsWith("asoc-")){
            var a=associations.findBySlug(slug.substring(5)).orElseThrow(()->new com.conlact.conlact_backend.exception.ResourceNotFoundException("Contenido no encontrado"));
            var request=new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false).convertValue(content,AssociationUpdateRequest.class);
            if(!validator.validate(request).isEmpty())throw new BadRequestException("Información de asociación inválida");
            associationService.updateAssociation(a.getId(),request);
        }
        return result;
    }
    private Set<UUID> previousIds(Map<String,Object> previous,String key){
        Set<UUID> ids=new HashSet<>();if(previous!=null && previous.get(key) instanceof List<?> list) for(Object item:list) if(item instanceof Map<?,?> map){UUID id=uuid(map.get("id"));if(id!=null)ids.add(id);}return ids;
    }
    private UUID uuid(Object id){try{return UUID.fromString(String.valueOf(id));}catch(IllegalArgumentException e){return null;}}
    private String text(Map<String,Object> row,String key){return row.get(key)==null?null:String.valueOf(row.get(key));}
    private String required(Map<String,Object> row,String key){String value=text(row,key);if(value==null||value.isBlank())throw new BadRequestException("Campo obligatorio: "+key);return value;}
}
