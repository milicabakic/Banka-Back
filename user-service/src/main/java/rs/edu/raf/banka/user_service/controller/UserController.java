package rs.edu.raf.banka.user_service.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import rs.edu.raf.banka.user_service.controller.response_forms.*;
import rs.edu.raf.banka.user_service.model.User;
import rs.edu.raf.banka.user_service.security.OTPUtilities;
import rs.edu.raf.banka.user_service.service.UserService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Api(value = "UserControllerAPI", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    @Autowired
    private final UserService userService;

    @GetMapping("/users")
    @ApiOperation("Gets all users")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<List<User>>getUsers() {
        return ResponseEntity.ok().body(userService.getUsers());
    }

    @GetMapping("/user")
    @ApiOperation("Gets user by JWT token")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<User>getUser(@RequestHeader("Authorization") String token) {
        try {
            User user = userService.getUserByToken(token);
            return ResponseEntity.ok().body(user);
        } catch (AuthenticationException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/user")
    @ApiOperation("Patches user by JWT token")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<User>editUserFromToken(@RequestHeader("Authorization") String token, @RequestBody CreateUserForm createUserForm) {
        try {
            User user = userService.getUserByToken(token);
            if(!userService.hasEditPermissions(user, token))
                return ResponseEntity.badRequest().build();

            userService.editUser(user, createUserForm);

            return ResponseEntity.ok().body(userService.getUser(user.getUsername()));
        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/user/create")
    @ApiOperation("Create user with username, ime, prezime, email, jmbg, br_telefona, password, aktivan, pozicija")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<User>createUser(@RequestBody CreateUserForm createUserForm) {
        return ResponseEntity.ok().body(userService.createUser(createUserForm));
    }

    @DeleteMapping("/user/delete/{id}")
    @ApiOperation("Delete user with specific id")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<?>deleteUser(@PathVariable long id) {
        Optional<User> user = userService.getUserById(id);
        if(user.isPresent()){
            if(!userService.deleteUser(user.get())){
                return ResponseEntity.badRequest().body("Can't delete admin");
            }
            return ResponseEntity.ok().body(user.get().getUsername() + " disabled");
        }else {return ResponseEntity.badRequest().build();}
    }

    @PostMapping("/user/edit/{id}")
    @ApiOperation("Edit user with specific id,text fields are with existing data, the user can change them")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<?>editUser(@PathVariable long id, @RequestHeader("Authorization") String token, @RequestBody CreateUserForm createUserForm) {
        Optional<User> user = userService.getUserById(id);
        if(user.isPresent()){
            //Sonnar pass
            if (!userService.hasEditPermissions(user.get(), token))
                return ResponseEntity.badRequest().build();
            //if(user.isRequiresOtp())
            //    return ResponseEntity.badRequest().build();

            userService.editUser(user.get(), createUserForm);
            return ResponseEntity.ok().body(user.get().getUsername() + " edited");
        }else{
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/otp/generateSecret")
    @ApiOperation("Generates random secure identifier for 2FA")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<String>generateSeecret() {
        var seecret = OTPUtilities.generateTOTPSecretKey();
        return ResponseEntity.ok().body(seecret);
    }

    @PostMapping("/otp/generateQrImage")
    @ApiOperation("Generates qr code for 2FA in base64 format")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<String>generateOtpQrImage(@RequestBody OtpQRForm form) {

        var qr = OTPUtilities.createTOTPQRCodeBase64Png(form.getSecret(), form.getLabel(), "Banka");
        return ResponseEntity.ok().body(qr);
    }

    @PostMapping("/otp/generateQrUri")
    @ApiOperation("Generates text behind qr code for 2FA in base64 format")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<String>generateOtpQrUri(@RequestBody OtpQRForm form) {

        var qr = OTPUtilities.createTOTPQrUri(form.getSecret(), form.getLabel(), "Banka");
        return ResponseEntity.ok().body(qr);
    }

    @PostMapping("/otp/validate")
    @ApiOperation("Validates otp code for specific 2FA identifier")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<Boolean>validateOtp(@RequestBody OtpToSecretForm form) {

        var valid = OTPUtilities.validate(form.getSecret(), form.getOtp());
        return ResponseEntity.ok().body(valid);
    }


    //TODO: Dodati permisije na otp urleove

    @PostMapping("/otp/set/{id}")
    @ApiOperation("Edit users 2FA secret with specific user id")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<?>setOtp(@PathVariable long id, @RequestBody String secret, @RequestHeader("Authorization") String token) {
        var user = userService.getUserById(id).get();
        if(!userService.hasEditPermissions(user, token))
            return ResponseEntity.badRequest().build();
        if(!OTPUtilities.isValidSeecret(secret))
            return ResponseEntity.badRequest().build();
        user.setOtpSeecret(secret);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/clear/{id}")
    @ApiOperation("Removes users 2FA secret with specific user id")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<?>clearOtp(@PathVariable long id, @RequestHeader("Authorization") String token) {
        var user = userService.getUserById(id).get();
        if(!userService.hasEditPermissions(user, token))
            return ResponseEntity.badRequest().build();
        if(user.isRequiresOtp())
            return ResponseEntity.badRequest().build();
        user.setOtpSeecret(null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/requires/{username}")
    @ApiOperation("Checks if user requires 2FA code")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<Boolean>requiresOtp(@PathVariable String username) {
        var requires = userService.getUser(username).isRequiresOtp();
        return ResponseEntity.ok().body(requires);
    }

    @PostMapping("/otp/has/{username}")
    @ApiOperation("Checks if user has 2FA set up")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = User.class)})
    public ResponseEntity<Boolean>hasOtp(@PathVariable String username) {
        var requires = userService.getUser(username).hasOTP();
        return ResponseEntity.ok().body(requires);
    }

    @PostMapping("/user/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordForm resetPasswordForm){
        if(!userService.resetPassword(resetPasswordForm.getEmail())){
            return ResponseEntity.badRequest().body("Mail failed to send");
        }
        return ResponseEntity.ok().body("Mail send to: " + resetPasswordForm.getEmail());
    }

    @PostMapping("/user/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String token, @RequestBody ChangePasswordForm changePasswordForm){
        if(!userService.setNewPassword(changePasswordForm.getNewPassword(), token)){
            return ResponseEntity.badRequest().body("Invalid token!");
        }
        return ResponseEntity.ok().body("New password!");
    }

    @PostMapping("/user/new-password/{id}")
    public ResponseEntity<?> changePasswordInternal(@PathVariable long id, @RequestBody ChangePasswordForm changePasswordForm){
        Optional<User> user = userService.getUserById(id);
        if(user.isPresent()){
            if(!userService.changePassword(changePasswordForm.getNewPassword(), user.get())){
                return ResponseEntity.badRequest().body("Check your pass again");
            }
            return ResponseEntity.ok().body("Password changed for user " + user.get().getUsername());
        }else {return ResponseEntity.badRequest().body("Invalid id");}
    }

    @PostMapping("/user/getId/{token}")
    public ResponseEntity<?> getUserId(@PathVariable String token){
        var id = userService.getUserId(token);
        if(id == null){
            return ResponseEntity.badRequest().body("Invalid token!");
        }
        return ResponseEntity.ok().body(id);
    }
}
