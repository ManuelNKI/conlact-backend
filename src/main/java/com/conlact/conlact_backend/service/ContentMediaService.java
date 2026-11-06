package com.conlact.conlact_backend.service;
import com.conlact.conlact_backend.storage.IStorage;
import com.conlact.conlact_backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class ContentMediaService {
    private final IStorage storage;
    public String image(String value,String bucket){
        if(value==null||!value.startsWith("data:"))return value;
        int comma=value.indexOf(',');if(comma<0)throw new BadRequestException("Imagen inválida");
        String header=value.substring(0,comma);String type=header.replace("data:","").replace(";base64","");
        String ext=switch(type){case "image/png"->".png";case "image/jpeg"->".jpg";case "image/webp"->".webp";default->throw new BadRequestException("Usa imágenes JPEG, PNG o WebP");};
        byte[] bytes;try{bytes=Base64.getDecoder().decode(value.substring(comma+1));}catch(IllegalArgumentException e){throw new BadRequestException("Imagen inválida");}
        if(bytes.length>5*1024*1024)throw new BadRequestException("La imagen supera los 5 MB");
        String path=UUID.randomUUID()+ext;storage.upload(bucket,path,bytes,type);return storage.getPublicUrl(bucket,path);
    }
    @SuppressWarnings("unchecked") public Object persist(Object value,String bucket){
        if(value instanceof String s)return image(s,bucket);
        if(value instanceof List<?> list)return list.stream().map(v->persist(v,bucket)).toList();
        if(value instanceof Map<?,?> map){Map<String,Object> result=new LinkedHashMap<>();map.forEach((k,v)->result.put(String.valueOf(k),persist(v,bucket)));return result;}
        return value;
    }
}
