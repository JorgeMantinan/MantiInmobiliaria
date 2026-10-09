package es.manti.profesionales.api.dto;

import es.manti.profesionales.domain.ChatVisto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapeador del diagnóstico de Telegram (MapStruct, bean Spring). El dominio
 * devuelve {@link ChatVisto} y la API el anidado
 * {@code TelegramDiagnosticoResponse.ChatVisto} de nombre idéntico pero tipo
 * distinto: MapStruct genera la conversión elemento a elemento.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DiagnosticoTelegramMapper {

    /** Envoltorio {chatsVistos: [...]}; el mapeo de la lista es el de abajo. */
    default TelegramDiagnosticoResponse toResponse(List<ChatVisto> chats) {
        return new TelegramDiagnosticoResponse(toChatVistoList(chats));
    }

    @Mapping(target = "chatId", source = "chatId")
    TelegramDiagnosticoResponse.ChatVisto toChatVisto(ChatVisto chat);

    List<TelegramDiagnosticoResponse.ChatVisto> toChatVistoList(List<ChatVisto> chats);
}
