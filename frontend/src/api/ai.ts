import { api } from "./client";
import type {
  CampaignType,
  Channel as CampaignChannel,
} from "@/schemas/campaign";
import type { Channel as TemplateChannel } from "@/schemas/template";
import type { EventCategorySummary } from "@/api/event";
import {
  fromBackendTree,
  toBackendTree,
  type BackendGroupNode,
  type GroupNode,
} from "@/schemas/segment";

export interface CampaignDraftContext {
  campaignName?: string;
  campaignDescription?: string;
  campaignType?: CampaignType;
  channel?: CampaignChannel;
  segmentId?: number | null;
  segmentName?: string | null;
}

export interface CampaignDraftRequest {
  prompt: string;
  context?: CampaignDraftContext;
  availableVariables?: string[];
}

export interface CampaignDraftResponse {
  campaign: {
    campaignName?: string;
    campaignDescription?: string;
    campaignType?: CampaignType;
    channel?: CampaignChannel;
  };
  template: {
    messageTemplateName: string;
    messageTemplateDescription: string;
    channel: TemplateChannel;
    messageTemplateTitle: string;
    messageTemplateBody: string;
    messageTemplateImageUrl?: string | null;
    requiredVariables?: string[];
  };
  notes?: string[];
}

export interface SegmentDraftContext {
  name?: string;
  description?: string;
  currentConditionTree?: GroupNode | null;
}

export interface SegmentDraftRequest {
  prompt: string;
  context?: SegmentDraftContext;
  availableEvents?: Pick<
    EventCategorySummary,
    "categoryId" | "categoryName" | "displayName" | "description"
  >[];
  availableTagKeys?: string[];
}

interface SegmentDraftHttpRequest {
  prompt: string;
  availableEvents?: SegmentDraftRequest["availableEvents"];
  availableTagKeys?: string[];
  context?: Omit<SegmentDraftContext, "currentConditionTree"> & {
    currentConditionTree?: BackendGroupNode | null;
  };
}

interface SegmentDraftHttpResponse {
  name?: string;
  description?: string;
  conditionTree: BackendGroupNode;
  notes?: string[];
}

export interface SegmentDraftResponse {
  name?: string;
  description?: string;
  conditionTree: GroupNode;
  notes?: string[];
}

export async function createCampaignDraft(
  projectId: number,
  req: CampaignDraftRequest,
): Promise<CampaignDraftResponse> {
  const res = await api.post<CampaignDraftResponse>(
    `/api/v1/projects/${projectId}/ai/campaign-draft`,
    req,
  );
  return res.data;
}

export async function createSegmentDraft(
  projectId: number,
  req: SegmentDraftRequest,
): Promise<SegmentDraftResponse> {
  const body: SegmentDraftHttpRequest = {
    prompt: req.prompt,
    availableEvents: req.availableEvents,
    availableTagKeys: req.availableTagKeys,
    context: req.context
      ? {
          name: req.context.name,
          description: req.context.description,
          currentConditionTree: req.context.currentConditionTree
            ? toBackendTree(req.context.currentConditionTree)
            : null,
        }
      : undefined,
  };
  const res = await api.post<SegmentDraftHttpResponse>(
    `/api/v1/projects/${projectId}/ai/segment-draft`,
    body,
  );
  return {
    ...res.data,
    conditionTree: fromBackendTree(res.data.conditionTree),
  };
}
