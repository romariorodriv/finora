package com.finora.app.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User,Long>{ Optional<User> findByEmailIgnoreCase(String email);
 Optional<User> findByGoogleSubject(String subject);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id")
 Optional<User> locked(@org.springframework.data.repository.query.Param("id")Long id); }
