package jungle.spaceship.photo.service;

import jungle.spaceship.jwt.SecurityUtil;
import jungle.spaceship.member.entity.Member;
import jungle.spaceship.photo.controller.dto.CommentRegisterDto;
import jungle.spaceship.photo.controller.dto.CommentResponseDto;
import jungle.spaceship.photo.entity.Comment;
import jungle.spaceship.photo.entity.Photo;
import jungle.spaceship.photo.repository.CommentRepository;
import jungle.spaceship.photo.repository.PhotoRepository;
import jungle.spaceship.response.BasicResponse;
import jungle.spaceship.response.ExtendedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.NoSuchElementException;
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {
    private final SecurityUtil securityUtil;
    private final CommentRepository commentRepository;
    private final PhotoRepository photoRepository;

    /**
     * 댓글 등록
     */
    @Transactional
    public BasicResponse registerComment(CommentRegisterDto commentRegisterDto) {
        Member member = securityUtil.extractMember();

        Photo photo =
                photoRepository.findById(commentRegisterDto.photoId())
                        .orElseThrow(() -> new NoSuchElementException("해당하는 사진이 없습니다"));

        Comment comment = commentRegisterDto.toComment(photo, member);
        commentRepository.save(comment);

        CommentResponseDto commentResponseDto = new CommentResponseDto(comment);

        return new ExtendedResponse<>(commentResponseDto, HttpStatus.CREATED.value(), "댓글이 생성되었습니다");
    }

}
