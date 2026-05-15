import { useMutation } from "@tanstack/react-query";
import * as aiApi from "@/api/ai";
import type {
  CampaignDraftRequest,
  CampaignDraftResponse,
  SegmentDraftRequest,
  SegmentDraftResponse,
} from "@/api/ai";

export function useCampaignDraftMutation(projectId: number) {
  return useMutation<CampaignDraftResponse, unknown, CampaignDraftRequest>({
    mutationFn: (req) => aiApi.createCampaignDraft(projectId, req),
  });
}

export function useSegmentDraftMutation(projectId: number) {
  return useMutation<SegmentDraftResponse, unknown, SegmentDraftRequest>({
    mutationFn: (req) => aiApi.createSegmentDraft(projectId, req),
  });
}
