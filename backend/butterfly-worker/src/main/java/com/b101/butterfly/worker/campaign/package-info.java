/**
 * 캠페인 발송 워커 패키지.
 *
 * <p>CampaignSendInstruction을 consume하여 DeliveryCommand로 변환하는 책임.
 *
 * <p>흐름:
 * <ol>
 *   <li>campaign.send.requested 토픽에서 CampaignSendInstruction consume</li>
 *   <li>templateId로 MessageTemplate 조회</li>
 *   <li>segmentId로 활성 사용자 목록 조회</li>
 *   <li>사용자 연락처 정보 조회</li>
 *   <li>DeliveryCommand 조립</li>
 *   <li>delivery-requests 토픽으로 produce</li>
 * </ol>
 */
package com.b101.butterfly.worker.campaign;
