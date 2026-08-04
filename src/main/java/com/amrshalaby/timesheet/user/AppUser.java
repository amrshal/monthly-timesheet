package com.amrshalaby.timesheet.user;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import java.time.Instant;

@MappedEntity("user")
public class AppUser {
 @Id @GeneratedValue private Long id;
 @MappedProperty private String email;
 @MappedProperty("display_name") private String displayName;
 @MappedProperty("password_hash") private String passwordHash;
 @MappedProperty @TypeDef(type = DataType.STRING) private UserRole role;
 @MappedProperty("manager_id") private Long managerId;
 @MappedProperty private boolean active;
 @MappedProperty("must_change_password") private boolean mustChangePassword;
 @DateCreated @MappedProperty("created_at") private Instant createdAt;
 @DateUpdated @MappedProperty("updated_at") private Instant updatedAt;
 @Version private Long version;
 public Long getId(){return id;} public void setId(Long id){this.id=id;}
 public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
 public String getDisplayName(){return displayName;} public void setDisplayName(String displayName){this.displayName=displayName;}
 public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String passwordHash){this.passwordHash=passwordHash;}
 public UserRole getRole(){return role;} public void setRole(UserRole role){this.role=role;}
 public Long getManagerId(){return managerId;} public void setManagerId(Long managerId){this.managerId=managerId;}
 public boolean isActive(){return active;} public void setActive(boolean active){this.active=active;}
 public boolean isMustChangePassword(){return mustChangePassword;} public void setMustChangePassword(boolean mustChangePassword){this.mustChangePassword=mustChangePassword;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Long getVersion(){return version;} public void setVersion(Long version){this.version=version;}
}
