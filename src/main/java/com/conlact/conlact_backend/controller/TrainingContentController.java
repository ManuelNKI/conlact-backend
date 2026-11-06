package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.entity.PageContent;
import com.conlact.conlact_backend.repository.PageContentRepository;
import com.conlact.conlact_backend.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor
public class TrainingContentController {
    private final PageContentRepository repository;
    @SuppressWarnings("unchecked") private List<Map<String,Object>> sections(){
        return repository.findById("capacitacion").map(p -> (List<Map<String,Object>>)p.getContenido().getOrDefault("secciones",new ArrayList<>())).orElseGet(ArrayList::new);
    }
    private void save(List<Map<String,Object>> sections){PageContent page=repository.findById("capacitacion").orElseGet(PageContent::new);page.setSlug("capacitacion");page.setTitulo("Capacitación");page.setContenido(Map.of("secciones",sections));repository.save(page);}
    @GetMapping("/api/admin/secciones") @PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> adminList(){return sections();}
    @GetMapping("/api/capacitacion") public List<Map<String,Object>> published(){
        return sections().stream().filter(s->Boolean.TRUE.equals(s.get("publicado"))).map(s->{var copy=new LinkedHashMap<>(s);copy.put("contenidos",items(s).stream().filter(c->Boolean.TRUE.equals(c.get("publicado"))).toList());return (Map<String,Object>)copy;}).toList();
    }
    @PostMapping("/api/admin/secciones") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> createSection(@RequestBody Map<String,Object> data){validate(data);var list=sections();var item=make(data,list.size());item.put("contenidos",new ArrayList<>());list.add(item);save(list);return item;}
    @PatchMapping("/api/admin/secciones/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> updateSection(@PathVariable String id,@RequestBody Map<String,Object> data){var list=sections();var item=find(list,id);update(list,item,data);save(list);return item;}
    @DeleteMapping("/api/admin/secciones/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void deleteSection(@PathVariable String id){var list=sections();list.remove(find(list,id));save(list);}
    @PostMapping("/api/admin/contenidos") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> createItem(@RequestBody Map<String,Object> data){validate(data);validateContent(data);var list=sections();var section=find(list,String.valueOf(data.get("seccion_id")));var items=items(section);var item=make(data,items.size());items.add(item);section.put("contenidos",items);save(list);return item;}
    @PatchMapping("/api/admin/contenidos/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> updateItem(@PathVariable String id,@RequestBody Map<String,Object> data){var list=sections();for(var section:list){var items=items(section);if(items.stream().anyMatch(i->id.equals(i.get("id")))){var item=find(items,id);var merged=new LinkedHashMap<>(item);merged.putAll(data);validateContent(merged);update(items,item,data);save(list);return item;}}throw new ResourceNotFoundException("Contenido no encontrado");}
    @DeleteMapping("/api/admin/contenidos/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void deleteItem(@PathVariable String id){var list=sections();for(var section:list){var items=items(section);if(items.removeIf(i->id.equals(i.get("id")))){save(list);return;}}throw new ResourceNotFoundException("Contenido no encontrado");}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> items(Map<String,Object> section){return (List<Map<String,Object>>)section.get("contenidos");}
    private Map<String,Object> find(List<Map<String,Object>> list,String id){return list.stream().filter(i->id.equals(i.get("id"))).findFirst().orElseThrow(()->new ResourceNotFoundException("Contenido no encontrado"));}
    private Map<String,Object> make(Map<String,Object> data,int order){var item=new LinkedHashMap<>(data);item.put("id",UUID.randomUUID().toString());item.put("orden",order);item.put("publicado",Boolean.TRUE.equals(data.get("publicado")));item.put("creado_en",java.time.OffsetDateTime.now().toString());return item;}
    private void update(List<Map<String,Object>> list,Map<String,Object> item,Map<String,Object> data){
        if(data.containsKey("mover")){int index=list.indexOf(item),next=index+("arriba".equals(data.get("mover"))?-1:1);if(next<0||next>=list.size())throw new BadRequestException("Ya está en el extremo");Collections.swap(list,index,next);for(int i=0;i<list.size();i++)list.get(i).put("orden",i);}
        else{validate(data);data.forEach((key,value)->{if(!Set.of("id","contenidos","seccion_id","creado_en").contains(key))item.put(key,value);});item.put("actualizado_en",java.time.OffsetDateTime.now().toString());}
    }
    private void validate(Map<String,Object> data){if(data.containsKey("titulo") && String.valueOf(data.get("titulo")).trim().length()<3)throw new BadRequestException("El título debe tener al menos 3 caracteres");}
    private void validateContent(Map<String,Object> data){String type=String.valueOf(data.get("tipo"));if(!Set.of("video","texto","enlace","documento").contains(type))throw new BadRequestException("Tipo de contenido inválido");String key=type.equals("texto")?"cuerpo":"url";if(data.get(key)==null||String.valueOf(data.get(key)).isBlank())throw new BadRequestException("Contenido incompleto");}
}
