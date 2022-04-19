package rs.edu.raf.banka.user_service.service.implementation;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import rs.edu.raf.banka.user_service.controller.response_forms.CreateUserForm;
import rs.edu.raf.banka.user_service.mail.PasswordResetToken;
import rs.edu.raf.banka.user_service.model.Permissions;
import rs.edu.raf.banka.user_service.model.Role;
import rs.edu.raf.banka.user_service.model.User;
import rs.edu.raf.banka.user_service.repository.PasswordTokenRepository;
import rs.edu.raf.banka.user_service.repository.RoleRepository;
import rs.edu.raf.banka.user_service.repository.UserRepository;
import rs.edu.raf.banka.user_service.service.UserService;
import org.springframework.jms.core.JmsTemplate;
import javax.jms.Queue;
import org.springframework.messaging.MessagingException;

import javax.transaction.Transactional;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
@Slf4j
public class UserServiceImplementation implements UserService, UserDetailsService {

    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final RoleRepository roleRepository;
    @Autowired
    private final PasswordTokenRepository passwordTokenRepository;
    @Autowired
    JmsTemplate jmsTemplate;
    @Autowired
    Queue mailQueue;

    @Autowired
    public UserServiceImplementation(UserRepository userRepository, RoleRepository roleRepository, PasswordTokenRepository passwordTokenRepository){
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordTokenRepository = passwordTokenRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username).get();
        if(user == null || !(user.isAktivan())){
            log.error("User {} not found in database", username);
            throw new UsernameNotFoundException("User not found in database");
        }
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        user.getRole().getPermissions().forEach(permission -> {
            authorities.add(new SimpleGrantedAuthority(permission));
        });

        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), authorities);
    }

    @Override
    public User getUser(String username) {
        log.info("Showing user {}", username);
        if(userRepository.findByUsername(username).isEmpty()){
            return null;
        }
        return userRepository.findByUsername(username).get();
    }

    @Override
    public Optional<User> getUserById(long id) {
        return userRepository.findById(id);
    }

    @Override
    public List<User> getUsers() {
        log.info("Showing list of users");
        return userRepository.findAll();
    }

    @Override
    public Role getRole(String role_name) {
        return roleRepository.findByName(role_name);
    }

    @Override
    public boolean deleteUser(User user){
        if(user.getRole().getName().equalsIgnoreCase("ROLE_GL_ADMIN")){
            return false;
        }

        log.info("Setting user inactive {} in database", user.getUsername());
        user.setAktivan(false);
        return true;
    }

    @Override
    public User createUser(CreateUserForm createUserForm) {
        String username = createUserForm.getIme().toLowerCase()+ "." + createUserForm.getPrezime().toLowerCase();
        if(this.getUser(username) instanceof User){
            username = username + (int)(Math.random() * (100)) + 1;
        }
        String password = createUserForm.getIme() + "Test123";

        User user = new User(username, createUserForm.getIme(),
                            createUserForm.getPrezime(), createUserForm.getEmail(),
                            createUserForm.getJmbg(), createUserForm.getBr_telefon(),
                            password, null, true, false, this.getRole(createUserForm.getPozicija()));
        log.info("Saving new user {} to the database", user.getUsername());
        String hash_pw = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hash_pw);
        return userRepository.save(user);
    }

    @Override
    public User getUserByToken(String token) {
        try {
            DecodedJWT decodedToken = decodeToken(token);
            String username = decodedToken.getSubject();
            var user = userRepository.findByUsername(username);

            if (user == null || user.isPresent() || !(user.get().isAktivan())) {
                log.error("User {} not found in database", username);
                throw new UsernameNotFoundException("User not found in database");
            }

            return user.get();
        } catch (JWTVerificationException e) {
            // TODO find a better exception for this case
            throw new UsernameNotFoundException("Token is invalid");
        }
    }

    @Override
    public void createUserAdmin(User user){
        log.info("Saving admin to the database");
        String hash_pw = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hash_pw);
        user.setAktivan(true);
        userRepository.save(user);
    }

    @Override
    public boolean hasEditPermissions(User user, String token) {
        if(token.startsWith("Bearer "))
            token = token.substring("Bearer ".length());
        //Cupamo username i permisije iz tokena
        Algorithm algorithm = Algorithm.HMAC256("secret".getBytes());
        JWTVerifier verifier = JWT.require(algorithm).build();
        DecodedJWT decodedJWT = verifier.verify(token);

        String usernameFromJWT = decodedJWT.getSubject();

        //glavni admin moze biti editovan samo ako je ulogovan kao glavni amdin
        if(user.getUsername().equals("admin"))
        {
            if(usernameFromJWT.equalsIgnoreCase(user.getUsername()))
                return true;
            return false;
        }

        String[] permissionsFromJWT = decodedJWT.getClaim("permissions").asArray(String.class);
        //Ako si to ti, edituj se || Ako nisi mozda je admin || u suprotnom neko hoce da edituje drugog a nije admin
        if(user.getUsername().equalsIgnoreCase(usernameFromJWT) && hasEditPermission(permissionsFromJWT, Permissions.MY_EDIT))
            return true;
        if(hasEditPermission(permissionsFromJWT, Permissions.EDIT_USER))
            return true;
        return false;
    }

    @Override
    public Long getUserId(String token) {
        Algorithm algorithm = Algorithm.HMAC256("secret".getBytes());
        JWTVerifier verifier = JWT.require(algorithm).build();
        DecodedJWT decodedJWT = verifier.verify(token);

        String usernameFromJWT = decodedJWT.getSubject();
        var user = this.getUser(usernameFromJWT);
        if(user == null)
            return null;
        return user.getId();
    }

    @Override
    public void editUser(User user, CreateUserForm newUser) {
        user.setIme(newUser.getIme());
        user.setPrezime(newUser.getPrezime());
        user.setEmail(newUser.getEmail());
        user.setJmbg(newUser.getJmbg());
        user.setBr_telefon(newUser.getBr_telefon());
        user.setRole(getRole(newUser.getPozicija()));
        userRepository.save(user);
    }

    @Override
    public Role saveRole(Role role) {
        log.info("Saving new role {} to the database", role.getName());
        return roleRepository.save(role);
    }

    @Override
    public void setRoleToUser(String username, String role_name) {
        log.info("Adding role {} to user {} to the database", role_name, username);
        User user = userRepository.findByUsername(username).get();
        Role role = roleRepository.findByName(role_name);
        user.setRole(role);
    }

    public boolean hasEditPermission(String[] permissions,Permissions permission){
        if(Arrays.stream(permissions).anyMatch(pm -> pm.equalsIgnoreCase(String.valueOf(permission)))){
            return true;
        }
        return false;
    }

    public void createPasswordResetTokenForUser(User user, String token) {
        PasswordResetToken myToken = new PasswordResetToken(token, user);
        passwordTokenRepository.save(myToken);
    }

    @Override
    public User getUserByEmail(String email){
        if(userRepository.findByEmail(email).isEmpty()){
            return null;
        }
        return userRepository.findByEmail(email).get();
    }

    @Override
    public boolean resetPassword(String email){
        User user = this.getUserByEmail(email);
        if(user == null){
            return false;
        }

        String token = UUID.randomUUID().toString();
        this.createPasswordResetTokenForUser(user, token);
        this.sendMail(email, token);

        return true;
    }

    public boolean changePassword(String password, User user){
        //Checking password
        String regex = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(password);
        if(!matcher.matches()) return false;

        String hash_pw = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hash_pw);
        userRepository.save(user);
        return true;
    }

    public void sendMail(String email, String token) throws MessagingException {
        String to = email;
        String url = "localhost:8080/user/change-password/" + token;
        String subject = "Password reset";
        String content = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body>\n" +
                "\n" +
                "<h1>Link ka resetovanju passworda</h1>\n" +
                "\n" +
                "<p>"+ url +"</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>";
        jmsTemplate.convertAndSend(mailQueue , to + "###" + subject + "###" + content);
    }

    @Override
    public boolean setNewPassword(String password, String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length());
        }

        PasswordResetToken prt = this.passwordTokenRepository.findByToken(token);
        if(prt == null){
            return false;
        }

        //Checking password
        String regex = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(password);
        if(!matcher.matches()) throw new BadCredentialsException("Password: must have 8 characters,one uppercase and one digit minimum");

        User user = prt.getUser();
        String hash_pw = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hash_pw);

        this.userRepository.save(user);
        return true;
    }

    private DecodedJWT decodeToken(String token) throws JWTVerificationException {
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length());
        }

        Algorithm   algorithm = Algorithm.HMAC256("secret".getBytes());
        JWTVerifier verifier  = JWT.require(algorithm).build();
        DecodedJWT  decoded   = verifier.verify(token);
        return decoded;
    }
}
