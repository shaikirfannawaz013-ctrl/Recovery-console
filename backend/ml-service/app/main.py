from fastapi import FastAPI
from pydantic import BaseModel, Field

from .model import MODEL_VERSION, ScoringModels

app = FastAPI(title="Payment Recovery ML Service", version="1.0.0")
models = ScoringModels.train()


class ScoreRequest(BaseModel):
    reasonCode: str
    category: str
    amount: float = Field(ge=0)
    method: str
    hourOfDay: int = Field(ge=0, le=23)
    daysSinceSalary: int = Field(ge=0, le=31)
    customerFailedCount: int = Field(ge=0)
    customerRecoveredCount: int = Field(ge=0)
    tenureDays: int = Field(ge=0)
    customerRiskScore: int = Field(ge=0, le=100)
    velocity10m: int = Field(ge=0)
    newDevice: bool = False
    ipCityMismatch: bool = False


class Factor(BaseModel):
    feature: str
    impact: float


class ScoreResponse(BaseModel):
    recoveryProbability: float
    riskScore: int
    anomalyScore: float
    topFactors: list[Factor]
    signals: list[str]
    modelVersion: str


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest) -> dict:
    return models.score(req)


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "model": MODEL_VERSION}
