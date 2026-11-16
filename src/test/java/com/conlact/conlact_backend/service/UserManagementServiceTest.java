package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.user.CreateUserRequest;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.dto.user.UpdateUserRequest;
import com.conlact.conlact_backend.dto.user.UserListResponse;
import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.UserRole;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(profileRepository, "", "");
    }

    @Test
    @DisplayName("Listar: Retorna la lista de usuarios del equipo ordenados")
    void shouldListStaffUsers() {
        UUID id1 = UUID.randomUUID();
        Profile p1 = Profile.builder().id(id1).fullName("Admin User").email("admin@conlact.ec").role(UserRole.admin).isActive(true).createdAt(OffsetDateTime.now()).build();
        when(profileRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(p1));

        UserListResponse response = service.listUsers();

        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).email()).isEqualTo("admin@conlact.ec");
        assertThat(response.users().get(0).role()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Crear: Éxito al registrar nuevo usuario admin o editor")
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest("Carlos Ruiz", "carlos@conlact.ec", "editor", "Segura123!", "Segura123!");
        when(profileRepository.existsByEmailIgnoreCase("carlos@conlact.ec")).thenReturn(false);
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> {
            Profile p = inv.getArgument(0);
            p.setCreatedAt(OffsetDateTime.now());
            return p;
        });

        StaffUserResponse response = service.createUser(request);

        assertThat(response.fullName()).isEqualTo("Carlos Ruiz");
        assertThat(response.email()).isEqualTo("carlos@conlact.ec");
        assertThat(response.role()).isEqualTo("editor");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Crear: Falla si el correo ya existe (409 Conflict)")
    void shouldFailWhenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest("Carlos Ruiz", "carlos@conlact.ec", "editor", "Segura123!", "Segura123!");
        when(profileRepository.existsByEmailIgnoreCase("carlos@conlact.ec")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está asociado a otra cuenta");
    }

    @Test
    @DisplayName("Crear: Falla si las contraseñas no coinciden")
    void shouldFailWhenPasswordsDoNotMatch() {
        CreateUserRequest request = new CreateUserRequest("Carlos Ruiz", "carlos@conlact.ec", "editor", "Segura123!", "Diferente123!");

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no coinciden");
    }

    @Test
    @DisplayName("Crear: Falla si la contraseña no cumple la política (sin dígitos o letras)")
    void shouldFailWhenPasswordDoesNotMeetPolicy() {
        CreateUserRequest noDigits = new CreateUserRequest("Carlos Ruiz", "carlos@conlact.ec", "editor", "Sololetras!", "Sololetras!");
        assertThatThrownBy(() -> service.createUser(noDigits))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("al menos una letra y un dígito");

        CreateUserRequest noLetters = new CreateUserRequest("Carlos Ruiz", "carlos@conlact.ec", "editor", "123456789", "123456789");
        assertThatThrownBy(() -> service.createUser(noLetters))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("al menos una letra y un dígito");
    }

    @Test
    @DisplayName("Actualizar: Rechazar petición vacía")
    void shouldRejectEmptyUpdateRequest() {
        UpdateUserRequest empty = new UpdateUserRequest(null, null, null);
        assertThatThrownBy(() -> service.updateUser(UUID.randomUUID(), empty, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Solo puedes actualizar nombre, rol y estado");
    }

    @Test
    @DisplayName("Actualizar: Prohibir auto-desactivación o auto-degradación de admin")
    void shouldPreventSelfDeactivationOrDegradation() {
        UUID myId = UUID.randomUUID();
        Profile myProfile = Profile.builder().id(myId).fullName("Yo Admin").role(UserRole.admin).isActive(true).build();
        when(profileRepository.findByIdForUpdate(myId)).thenReturn(Optional.of(myProfile));

        UpdateUserRequest deactivate = new UpdateUserRequest(null, null, false);
        assertThatThrownBy(() -> service.updateUser(myId, deactivate, myId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No puedes eliminar, desactivar o quitar el rol admin de tu propia cuenta");

        UpdateUserRequest degrade = new UpdateUserRequest(null, "editor", null);
        assertThatThrownBy(() -> service.updateUser(myId, degrade, myId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No puedes eliminar, desactivar o quitar el rol admin de tu propia cuenta");
    }

    @Test
    @DisplayName("Actualizar: Proteger al último administrador activo")
    void shouldProtectLastActiveAdminOnDeactivateOrDegrade() {
        UUID actorId = UUID.randomUUID();
        UUID otherAdminId = UUID.randomUUID();
        Profile otherAdmin = Profile.builder().id(otherAdminId).fullName("Otro Admin").role(UserRole.admin).isActive(true).build();
        when(profileRepository.findByIdForUpdate(otherAdminId)).thenReturn(Optional.of(otherAdmin));
        when(profileRepository.countByRoleAndIsActiveTrue(UserRole.admin)).thenReturn(1L);

        UpdateUserRequest deactivate = new UpdateUserRequest(null, null, false);
        assertThatThrownBy(() -> service.updateUser(otherAdminId, deactivate, actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Debe permanecer al menos un administrador activo");
    }

    @Test
    @DisplayName("Eliminar: Prohibir auto-eliminación")
    void shouldPreventSelfDeletion() {
        UUID myId = UUID.randomUUID();
        Profile myProfile = Profile.builder().id(myId).fullName("Yo Admin").role(UserRole.admin).isActive(true).build();
        when(profileRepository.findByIdForUpdate(myId)).thenReturn(Optional.of(myProfile));

        assertThatThrownBy(() -> service.deleteUser(myId, myId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No puedes eliminar, desactivar o quitar el rol admin de tu propia cuenta");
    }

    @Test
    @DisplayName("Eliminar: Proteger al último administrador activo")
    void shouldProtectLastActiveAdminOnDeletion() {
        UUID actorId = UUID.randomUUID();
        UUID otherAdminId = UUID.randomUUID();
        Profile otherAdmin = Profile.builder().id(otherAdminId).fullName("Otro Admin").role(UserRole.admin).isActive(true).build();
        when(profileRepository.findByIdForUpdate(otherAdminId)).thenReturn(Optional.of(otherAdmin));
        when(profileRepository.countByRoleAndIsActiveTrue(UserRole.admin)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(otherAdminId, actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Debe permanecer al menos un administrador activo");
    }
}
