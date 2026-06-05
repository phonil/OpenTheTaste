package com.ott.api_user.shortform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.common.web.exception.BusinessException;
import com.ott.common.web.exception.ErrorCode;
import com.ott.domain.click_event.domain.ClickEvent;
import com.ott.domain.click_event.domain.ClickType;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.member.repository.MemberRepository;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.domain.short_form.repository.ShortFormRepository;
import com.ott.domain.click_event.repository.ClickRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickEventServiceTest {

    @Mock
    private ClickRepository clickRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ShortFormRepository shortFormRepository;

    @InjectMocks
    private ClickEventService clickEventService;

    @Test
    void saveClickEvent_persistsEventWithClickType() {
        Long memberId = 1L;
        Long mediaId = 11L;
        Member member = Member.builder().id(memberId).email("m@ott").nickname("m").provider(Provider.KAKAO).role(Role.MEMBER).build();
        ShortForm shortForm = ShortForm.builder().id(101L).build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(shortFormRepository.findByMediaId(mediaId)).thenReturn(Optional.of(shortForm));

        // 정상 흐름에서 이벤트가 저장되고 ClickType/Member/ShortForm이 일치하는지 확인
        clickEventService.saveClickEvent(memberId, mediaId, ClickType.CTA_CLICK);

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickRepository).save(captor.capture());
        ClickEvent saved = captor.getValue();
        assertThat(saved.getClickType()).isEqualTo(ClickType.CTA_CLICK);
        assertThat(saved.getMember()).isSameAs(member);
        assertThat(saved.getShortForm()).isSameAs(shortForm);
    }

    @Test
    void saveClickEvent_throwsWhenMemberMissing() {
        when(memberRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clickEventService.saveClickEvent(5L, 1L, ClickType.SHORT_CLICK))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void saveClickEvent_throwsWhenShortFormMissing() {
        Member member = Member.builder().id(6L).email("u@ott").nickname("u").provider(Provider.KAKAO).role(Role.MEMBER).build();
        when(memberRepository.findById(6L)).thenReturn(Optional.of(member));
        when(shortFormRepository.findByMediaId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clickEventService.saveClickEvent(6L, 99L, ClickType.SHORT_CLICK))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHORT_FORM_NOT_FOUND);
    }
}
