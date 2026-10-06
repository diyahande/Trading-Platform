import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsString, Length } from 'class-validator';

export class RejectSignupRequestDto {
  @ApiProperty({ minLength: 2, maxLength: 500 })
  @IsString()
  @Length(2, 500)
  rejectionReason!: string;

  @ApiPropertyOptional({ maxLength: 1000 })
  @IsString()
  @Length(0, 1000)
  reviewNotes = '';
}
