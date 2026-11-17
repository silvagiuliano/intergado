package br.com.intergado.purchases;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users who may log in and their roles. Admin-only (Role.required). */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    public record UserView(String email, String name, Role role, boolean active, boolean fromEnv, String createdAt,
                           String createdBy, String updatedAt, String updatedBy, String lastLoginAt) {
        static UserView of(UserService.User user) {
            return new UserView(user.email(), user.name(), user.role(), user.active(), user.fromEnv(), user.createdAt(),
                user.createdBy(), user.updatedAt(), user.updatedBy(), user.lastLoginAt());
        }
    }

    public record RoleView(Role key, String label, String description) {}
    public record NewUser(String email, String name, String role) {}
    public record UserChanges(String name, String role, Boolean active) {}

    @GetMapping
    public List<UserView> list() {
        return users.list().stream().map(UserView::of).toList();
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        return Arrays.stream(Role.values()).map(role -> new RoleView(role, role.label, role.description)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@RequestBody NewUser input, HttpServletRequest request) {
        Role role = input.role() == null ? null : Role.parse(input.role());
        return UserView.of(users.create(input.email(), input.name(), role, actor(request)));
    }

    @PutMapping("/{email}")
    public UserView update(@PathVariable String email, @RequestBody UserChanges input, HttpServletRequest request) {
        Role role = input.role() == null ? null : Role.parse(input.role());
        return UserView.of(users.update(email, new UserService.Changes(input.name(), role, input.active()), actor(request)));
    }

    private static String actor(HttpServletRequest request) {
        return (String) request.getAttribute(AuthController.EMAIL_ATTRIBUTE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(IllegalStateException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(UserService.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(UserService.NotFound error) {
        return Map.of("error", error.getMessage());
    }
}
