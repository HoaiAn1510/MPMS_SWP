package com.example.manga_management.repository;

import com.example.manga_management.entity.FrameTask;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FrameTaskRepository extends JpaRepository<FrameTask, String> {

    Optional<FrameTask> findTopByOrderByIdDesc();

    List<FrameTask> findBySubmission_IdOrderByFrameNumberAsc(String submissionId);

    void deleteBySubmission_Id(String submissionId);
}
