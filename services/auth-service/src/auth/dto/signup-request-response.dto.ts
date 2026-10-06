import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class SignupRequestResponseDto {
  @ApiProperty({ format: 'uuid' })
  id!: string;

  @ApiProperty()
  firstName!: string;

  @ApiProperty()
  lastName!: string;

  @ApiProperty({ format: 'email' })
  username!: string;

  @ApiProperty()
  phone!: string;

  @ApiProperty({ format: 'date' })
  dob!: string;

  @ApiProperty({ enum: ['NEW', 'APPROVED', 'REJECTED'] })
  status!: string;

  @ApiPropertyOptional()
  reviewNotes?: string | null;

  @ApiPropertyOptional()
  rejectionReason?: string | null;

  @ApiPropertyOptional({ format: 'uuid' })
  approvedAccountId?: string | null;

  @ApiPropertyOptional({ format: 'uuid' })
  approvedAuthUserId?: string | null;

  @ApiPropertyOptional()
  reviewedBy?: string | null;

  @ApiPropertyOptional({ format: 'date-time' })
  reviewedOn?: string | null;

  @ApiProperty({ format: 'date-time' })
  createdOn!: string;

  @ApiProperty({ format: 'date-time' })
  updatedOn!: string;
}
