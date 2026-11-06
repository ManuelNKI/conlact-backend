package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.storage.IStorage;
import com.conlact.conlact_backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminUploadController {
    private final IStorage storage;
    @PostMapping("/api/admin/imagenes")
    public Map<String,String> upload(@RequestParam MultipartFile file, @RequestParam(defaultValue="product-images") String bucket) throws IOException {
        if(!Set.of("product-images","recipe-images","association-images").contains(bucket)) throw new BadRequestException("Bucket no permitido");
        if(file.isEmpty() || file.getSize()>5*1024*1024) throw new BadRequestException("La imagen debe pesar entre 1 byte y 5 MB");
        String type=file.getContentType();
        String extension = switch(type==null?"":type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new BadRequestException("Usa una imagen JPEG, PNG o WebP"); };
        String path=UUID.randomUUID()+extension;
        storage.upload(bucket,path,file.getBytes(),type);
        return Map.of("storage_path",path,"url",storage.getPublicUrl(bucket,path));
    }
}
