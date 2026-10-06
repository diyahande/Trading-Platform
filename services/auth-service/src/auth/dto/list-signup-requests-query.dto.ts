import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsOptional } from 'class-validator';

export class ListSignupRequestsQueryDto {
  @ApiPropertyOptional({ enum: ['NEW', 'APPROVED', 'REJECTED'] })
  @IsOptional()
  @IsIn(['NEW', 'APPROVED', 'REJECTED'])
  status?: 'NEW' | 'APPROVED' | 'REJECTED';
}
