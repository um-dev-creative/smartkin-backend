package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/// The one place that creates a child profile. Both ways into a family (accepting an invitation and creating the child's account) go through it,
/// so a profile is never created without the parent's explicit action or the child's own consent (S-2).
@Component
public class ChildLinker {

    private final ParentRepository parentRepository;
    private final ChildRepository childRepository;

    /// Creates the linker.
    ///
    /// @param parentRepository the parent repository
    /// @param childRepository  the child repository
    public ChildLinker(ParentRepository parentRepository, ChildRepository childRepository) {
        this.parentRepository = parentRepository;
        this.childRepository = childRepository;
    }

    /// Tells whether the user has no parent or child profile yet, i.e. can become a child.
    ///
    /// @param userId the user id
    /// @return `true` when the user has no profile
    public boolean isFree(UUID userId) {
        return !childRepository.existsById(userId) && !parentRepository.existsById(userId);
    }

    /// Creates the child profile and flushes it, so a concurrent duplicate fails here and not at commit.
    ///
    /// @param parent     the parent the child joins
    /// @param childId    the child's backbone user id
    /// @param gradeLevel school grade, may be null
    /// @param avatar     avatar reference, may be null
    /// @return the saved child
    public ChildEntity create(ParentEntity parent, UUID childId, String gradeLevel, String avatar) {
        var child = new ChildEntity();
        child.setId(childId);
        child.setParent(parent);
        child.setGradeLevel(gradeLevel);
        child.setAvatar(avatar);
        child.setTotalPoints(0);
        return childRepository.saveAndFlush(child);
    }
}
