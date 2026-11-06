package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.entity.PageContent;
import com.conlact.conlact_backend.repository.PageContentRepository;
import com.conlact.conlact_backend.service.*;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PageContentControllerTest {
 @Test void savesCanonicalContentAndConfigurationTogether() {
  var repo=mock(PageContentRepository.class);var media=mock(ContentMediaService.class);var editorial=mock(EditorialContentService.class);
  var controller=new PageContentController(repo,media,editorial);var content=Map.<String,Object>of("atractivos",List.of());
  when(repo.findById("turismo")).thenReturn(Optional.empty());when(media.persist(content,"association-images")).thenReturn(content);when(editorial.sync("turismo",content,null)).thenReturn(content);when(repo.save(any())).thenAnswer(i->i.getArgument(0));
  var result=controller.save("turismo",new PageContentController.PageRequest("Rutas","Turismo","Pilahuín",content));
  assertThat(result.getSlug()).isEqualTo("turismo");assertThat(result.getContenido()).isEqualTo(content);verify(repo).save(any(PageContent.class));
 }
 @Test void aMissingPageDoesNotCreateDataOnPublicRead() {
  var repo=mock(PageContentRepository.class);when(repo.findById("footer")).thenReturn(Optional.empty());
  var controller=new PageContentController(repo,mock(ContentMediaService.class),mock(EditorialContentService.class));
  assertThatThrownBy(()->controller.get("footer")).isInstanceOf(ResourceNotFoundException.class);verify(repo,never()).save(any());
 }
}
